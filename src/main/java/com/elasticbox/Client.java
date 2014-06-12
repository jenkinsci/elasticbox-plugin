/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

/**
 *
 * @author Phong Nguyen Le
 */
public class Client {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String JSON_CONTENT_TYPE = "application/json";    
    private static final String DEPLOYMENT_PROFILE_SCHEMA = "http://elasticbox.net/schemas/2014-06-04/deploy-instance-request";

    public static interface InstanceState {
        String PROCESSING = "processing";
        String DONE = "done";
        String UNAVAILABLE = "unavailable";
    }
    public static final Set FINISH_STATES = new HashSet(Arrays.asList(InstanceState.DONE, InstanceState.UNAVAILABLE));
    public static final Set TERMINATE_OPERATIONS = new HashSet(Arrays.asList("terminate", "terminate_service"));
    public static final Set ON_OPERATIONS = new HashSet(Arrays.asList("deploy", "snapshot", "reinstall", "reconfigure"));
    public static final Set OFF_OPERATIONS = new HashSet(Arrays.asList("shutdown", "shutdown_service", "terminate", "terminate_service"));

    private final HttpClient httpClient;
    private final String endpointUrl;
    private final String username;
    private final String password;
    private String token = null;

    public Client(String endpointUrl, String username, String password) {
        this.httpClient = new HttpClient();
        this.endpointUrl = endpointUrl.endsWith("/") ? endpointUrl.substring(0, endpointUrl.length() - 1) : endpointUrl;
        this.username = username;
        this.password = password;
    }
        
    public void connect() throws IOException {
        PostMethod post = new PostMethod(MessageFormat.format("{0}/services/security/token", endpointUrl));
        JSONObject json = new JSONObject();
        json.put("email", this.username);
        json.put("password", this.password);
        post.setRequestEntity(new StringRequestEntity(json.toString(), JSON_CONTENT_TYPE, "utf-8"));
        try {
            int status = httpClient.executeMethod(post);
            if (status != HttpStatus.SC_OK) {
                throw new IOException(MessageFormat.format("Error {0} connecting to ElasticBox at {1}: {2}", status, this.endpointUrl,
                        getErrorMessage(post.getResponseBodyAsString())));
            }
            token = post.getResponseBodyAsString();            
        } finally {
            post.releaseConnection();
        }
    }
    
    public JSONArray getWorkspaces() throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces", endpointUrl), true);
    }
    
    public JSONArray getBoxes(String workspaceId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces/{1}/boxes", endpointUrl, workspaceId), true);
    }
    
    public JSONArray getProfiles(String workspaceId, String boxId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces/{1}/profiles?box_version={2}", endpointUrl, workspaceId, boxId), true);
    }
    
    public JSONObject getInstance(String instanceId) throws IOException {
        return (JSONObject) doGet(MessageFormat.format("{0}/services/instances/{1}", endpointUrl, instanceId), false);
    }

    public JSONObject getProfile(String profileId) throws IOException {
        return (JSONObject) doGet(MessageFormat.format("{0}/services/profiles/{1}", endpointUrl, profileId), false);        
    }
    
    protected class ProgressMonitor implements IProgressMonitor {
        private final String instanceUrl;
        private final long creationTime;
        private final Object waitLock = new Object();
        
        private ProgressMonitor(String instanceUrl) {
            this.instanceUrl = instanceUrl;
            creationTime = System.currentTimeMillis();
        }

        public String getResourceUrl() {
            return instanceUrl;
        }
        
        public boolean isDone() throws IncompleteException, IOException {
            String state = getState();
            if (state.equals(InstanceState.UNAVAILABLE)) {
                throw new IncompleteException("The instance is unavailable");
            }
            
            return state.equals(InstanceState.DONE);
        }

        public void waitForDone(int timeout) throws IncompleteException, IOException {
            String state = waitFor(FINISH_STATES, timeout);
            if (state.equals(InstanceState.UNAVAILABLE)) {
                throw new IncompleteException("The instance is unavailable");
            }
            else if (!state.equals(InstanceState.DONE)) {
                throw new IncompleteException(
                        MessageFormat.format("The instance at {0} is not in ready after waiting for {1} minutes. Current instance state: {2}",
                                instanceUrl, timeout, state));
            }
        }
        
        private String getState() throws IOException {
            JSONObject instance = (JSONObject) doGet(instanceUrl, false);
            return instance.getString("state");            
        }
        
        /**
         * 
         * @param states
         * @param timeout in minutes
         * @return the latest state if it is one of the specified states or the timeout elapsed
         * @throws IOException 
         */
        private String waitFor(Set<String> states, int timeout) throws IOException {
            long startTime = System.currentTimeMillis();
            long remainingTime = timeout * 60000;
            String state = null;
            synchronized(waitLock) {
                while(remainingTime > 0 && !states.contains((state = getState()))) {
                    try {
                        waitLock.wait(1000);
                    } catch (InterruptedException ex) {
                    }
                    long currentTime = System.currentTimeMillis();
                    remainingTime =  remainingTime - (currentTime - startTime);
                    startTime = currentTime;
                }
            }
            return state;
        }

        public long getCreationTime() {
            return this.creationTime;
        }
    }
    
    public IProgressMonitor deploy(String profileId, String workspaceId, String environment, int instances, Map<String, String> variables) throws IOException {
        JSONObject profile = (JSONObject) doGet(MessageFormat.format("{0}/services/profiles/{1}", endpointUrl, profileId), false);
        JSONObject serviceProfile = profile.getJSONObject("profile");
        if (serviceProfile.containsKey("instances")) {
            serviceProfile.put("instances", instances);
        }
        JSONArray jsonVars = new JSONArray();
        for (Map.Entry<String, String> entry : variables.entrySet()) {            
            JSONObject jsonVar = new JSONObject();
            jsonVar.put("name", entry.getKey());
            jsonVar.put("type", "Text");
            jsonVar.put("value", entry.getValue());
            jsonVars.add(jsonVar);
        }
        JSONObject deployRequest = new JSONObject();
        deployRequest.put("schema", DEPLOYMENT_PROFILE_SCHEMA);
        deployRequest.put("environment", environment);
        deployRequest.put("profile", profile);
        deployRequest.put("owner", workspaceId);
        deployRequest.put("variables", jsonVars);
        
        PostMethod post = new PostMethod(MessageFormat.format("{0}/services/instances", endpointUrl));
        setRequiredHeaders(post);
        post.setRequestEntity(new StringRequestEntity(deployRequest.toString(), JSON_CONTENT_TYPE, "utf-8"));
        try {
            post = (PostMethod) executeMethod(post, HttpStatus.SC_ACCEPTED);
            JSONObject instance = JSONObject.fromObject(post.getResponseBodyAsString());
            return new ProgressMonitor(endpointUrl + instance.getString("uri"));
        } finally {
            post.releaseConnection();
        }
        
    }
    
    public IProgressMonitor terminate(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        ProgressMonitor monitor = new ProgressMonitor(instanceUrl);        
        String state = monitor.getState();
        String operation = state.equals(InstanceState.DONE) ? "terminate" : "force_terminate";
        HttpMethod delete = new DeleteMethod(MessageFormat.format("{0}?operation={1}", instanceUrl, operation));
        try {
            executeMethod(delete, HttpStatus.SC_ACCEPTED);
        } finally {
            delete.releaseConnection();
        }
        return monitor;
    }
    
    public IProgressMonitor shutdown(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        HttpMethod put = new PutMethod(MessageFormat.format("{0}/shutdown", instanceUrl));
        try {
            executeMethod(put, HttpStatus.SC_ACCEPTED);
        } finally {
            put.releaseConnection();
        }
        return new ProgressMonitor(instanceUrl);
    }

    public void delete(String instanceId) throws IOException {
        HttpMethod delete = new DeleteMethod(MessageFormat.format("{0}?operation=delete", getInstanceUrl(instanceId)));
        try {
            executeMethod(delete, HttpStatus.SC_ACCEPTED);
        } finally {
            delete.releaseConnection();
        }
    }

    public JSON doGet(String url, boolean isArray) throws IOException {
        if (url.startsWith("/")) {
            url = endpointUrl + url;
        }
        HttpMethod get = new GetMethod(url);
        get = executeMethod(get, HttpStatus.SC_OK);
        return isArray ? JSONArray.fromObject(get.getResponseBodyAsString()) : JSONObject.fromObject(get.getResponseBodyAsString());                    
    }
    
    private String getInstanceUrl(String instanceId) {
        return MessageFormat.format("{0}/services/instances/{1}", endpointUrl, instanceId);
    }
    
    private String getErrorMessage(String errorResponseBody) {
        JSONObject error = null;
        try {
            error = JSONObject.fromObject(errorResponseBody);
        } catch (JSONException ex) {
            //
        } 
        return error != null ? error.getString("message") : errorResponseBody;
    }
    
    private void setRequiredHeaders(HttpMethod method) {
        method.setRequestHeader(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE);
        method.setRequestHeader("ElasticBox-Token", token);
    }
    
    private HttpMethod executeMethod(HttpMethod method, int expectedStatus) throws IOException {
        if (token == null) {
            connect();
        }
        setRequiredHeaders(method);
        int status = httpClient.executeMethod(method);
        if (status != expectedStatus) {
            if (status == HttpStatus.SC_UNAUTHORIZED) {
                token = null;
                method.releaseConnection();
                connect();
                HttpMethod oldMethod = method;
                try {
                    method = (HttpMethod) oldMethod.getClass().newInstance();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                method.setURI(oldMethod.getURI());
                for (Header header : oldMethod.getRequestHeaders()) {
                    method.setRequestHeader(header);
                }
                setRequiredHeaders(method);
                status = httpClient.executeMethod(method);                
            }
            if (status != expectedStatus) {
                token = null;
                throw new ClientException(getErrorMessage(method.getResponseBodyAsString()), status);
            }            
        }

        return method;
    }
    
}
