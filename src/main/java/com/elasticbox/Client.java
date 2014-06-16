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
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.util.EntityUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public class Client {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String JSON_CONTENT_TYPE = "application/json";    
    private static final String UTF_8 = "UTF-8";

    private static final String BASE_ELASTICBOX_SCHEMA = "http://elasticbox.net/schemas/";
    private static final String DEPLOYMENT_REQUEST_SCHEMA_NAME = "deploy-instance-request";
    
    public static interface InstanceState {
        String PROCESSING = "processing";
        String DONE = "done";
        String UNAVAILABLE = "unavailable";
    }
    public static final Set FINISH_STATES = new HashSet(Arrays.asList(InstanceState.DONE, InstanceState.UNAVAILABLE));
    public static final Set TERMINATE_OPERATIONS = new HashSet(Arrays.asList("terminate", "terminate_service"));
    public static final Set ON_OPERATIONS = new HashSet(Arrays.asList("deploy", "snapshot", "reinstall", "reconfigure"));
    public static final Set OFF_OPERATIONS = new HashSet(Arrays.asList("shutdown", "shutdown_service", "terminate", "terminate_service"));
    
    private static String getSchemaVersion(String url) {
        return url.substring(BASE_ELASTICBOX_SCHEMA.length(), url.indexOf('/', BASE_ELASTICBOX_SCHEMA.length()));
    }

    private final HttpClient httpClient;
    private final String endpointUrl;
    private final String username;
    private final String password;
    private String token = null;

    public Client(String endpointUrl, String username, String password) {
        this.httpClient = createHttpClient();
        this.endpointUrl = endpointUrl.endsWith("/") ? endpointUrl.substring(0, endpointUrl.length() - 1) : endpointUrl;
        this.username = username;
        this.password = password;
    }
        
    public void connect() throws IOException {
        HttpPost post = new HttpPost(MessageFormat.format("{0}/services/security/token", endpointUrl));
        JSONObject json = new JSONObject();
        json.put("email", this.username);
        json.put("password", this.password);
        post.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));
        HttpResponse response = httpClient.execute(post);
        int status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_OK) {
            throw new IOException(MessageFormat.format("Error {0} connecting to ElasticBox at {1}: {2}", status, this.endpointUrl,
                    getErrorMessage(getResponseBodyAsString(response))));
        }
        token = getResponseBodyAsString(response);            
    }
    
    public JSONArray getWorkspaces() throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces", endpointUrl), true);
    }
    
    public JSONArray getBoxes(String workspaceId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces/{1}/boxes", endpointUrl, URLEncoder.encode(workspaceId, UTF_8)), true);
    }
    
    public JSONArray getProfiles(String workspaceId, String boxId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces/{1}/profiles?box_version={2}", endpointUrl, URLEncoder.encode(workspaceId, UTF_8), boxId), true);
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
            String state;
            try {
                state = getState();
            } catch (ClientException ex) {
                if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                    throw new IncompleteException("The instance cannot be found");
                } else {
                    throw ex;
                }
            }
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
            while(remainingTime > 0 && !states.contains((state = getState()))) {
                synchronized(waitLock) {
                    try {
                        waitLock.wait(1000);
                    } catch (InterruptedException ex) {
                    }
                }
                long currentTime = System.currentTimeMillis();
                remainingTime =  remainingTime - (currentTime - startTime);
                startTime = currentTime;                
            }
            return state;
        }

        public long getCreationTime() {
            return this.creationTime;
        }
    }
    
    public IProgressMonitor deploy(String profileId, String workspaceId, String environment, int instances, Map<String, String> variables) throws IOException {
        JSONObject profile = (JSONObject) doGet(MessageFormat.format("/services/profiles/{0}", profileId), false);
        JSONObject deployRequest = new JSONObject();
        
        String profileSchema = profile.getString("schema");
        String schemaVersion = getSchemaVersion(profileSchema);
        if (schemaVersion.compareTo("2014-05-23") > 0) {
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
            deployRequest.put("schema", BASE_ELASTICBOX_SCHEMA + schemaVersion + '/' + DEPLOYMENT_REQUEST_SCHEMA_NAME);
            deployRequest.put("variables", jsonVars);
        } else {
            JSONObject mainInstance = (JSONObject) profile.getJSONArray("instances").get(0);
            JSONArray jsonVars = mainInstance.getJSONArray("variables");
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                JSONObject jsonVar = null;
                for (Object var : jsonVars) {
                    JSONObject json = (JSONObject) var;
                    if (entry.getKey().equals(json.getString("name"))) {
                        jsonVar = json;
                        break;
                    }
                }
                if (jsonVar == null) {
                    jsonVar = new JSONObject();
                    jsonVar.put("name", entry.getKey());
                    jsonVar.put("type", "Text");
                    jsonVar.put("value", entry.getValue());
                    jsonVars.add(jsonVar);
                } else {
                    jsonVar.put("value", entry.getValue());
                }
            }
            JSONObject serviceProfile = mainInstance.getJSONObject("profile");
            if (serviceProfile.containsKey("instances")) {
                serviceProfile.put("instances", instances);
            }                        
            deployRequest.put("schema", BASE_ELASTICBOX_SCHEMA + schemaVersion + "/deploy-service-request");
        }
        deployRequest.put("environment", environment);
        deployRequest.put("profile", profile);
        deployRequest.put("owner", workspaceId);        
        
        HttpPost post = new HttpPost(MessageFormat.format("{0}/services/instances", endpointUrl));
        post.setEntity(new StringEntity(deployRequest.toString(), ContentType.APPLICATION_JSON));
        try {
            HttpResponse response = execute(post, HttpStatus.SC_ACCEPTED);
            JSONObject instance = JSONObject.fromObject(getResponseBodyAsString(response));
            return new ProgressMonitor(endpointUrl + instance.getString("uri"));
        } finally {
            post.reset();
        }
    }
    
    public IProgressMonitor terminate(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        ProgressMonitor monitor = new ProgressMonitor(instanceUrl);        
        String state = monitor.getState();
        String operation = state.equals(InstanceState.DONE) ? "terminate" : "force_terminate";
        HttpDelete delete = new HttpDelete(MessageFormat.format("{0}?operation={1}", instanceUrl, operation));
        try {
            execute(delete, HttpStatus.SC_ACCEPTED);
            return monitor;
        } finally {
            delete.reset();
        }
    }
    
    public IProgressMonitor shutdown(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        HttpPut put = new HttpPut(MessageFormat.format("{0}/shutdown", instanceUrl));
        try {
            execute(put, HttpStatus.SC_ACCEPTED);
            return new ProgressMonitor(instanceUrl);
        } finally {
            put.reset();
        }
    }

    public void delete(String instanceId) throws IOException {
        HttpDelete delete = new HttpDelete(MessageFormat.format("{0}?operation=delete", getInstanceUrl(instanceId)));
        try {
            execute(delete, HttpStatus.SC_ACCEPTED);
        } finally {
            delete.reset();
        }
    }

    public JSON doGet(String url, boolean isArray) throws IOException {
        if (url.startsWith("/")) {
            url = endpointUrl + url;
        }
        HttpGet get = new HttpGet(url);
        try {
            HttpResponse response = execute(get, HttpStatus.SC_OK);
            return isArray ? JSONArray.fromObject(getResponseBodyAsString(response)) : JSONObject.fromObject(getResponseBodyAsString(response));                    
        } finally {
            get.reset();
        }
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
    
    private void setRequiredHeaders(HttpRequestBase request) {
        request.setHeader(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE);
        request.setHeader("ElasticBox-Token", token);
    }
    
    private static String getResponseBodyAsString(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        return entity != null ? EntityUtils.toString(entity) : null;
    }
    
    private HttpResponse execute(HttpRequestBase request, int expectedStatus) throws IOException {
        if (token == null) {
            connect();
        }
        setRequiredHeaders(request);
        HttpResponse response = httpClient.execute(request);
        int status = response.getStatusLine().getStatusCode();
        if (status != expectedStatus) {
            if (status == HttpStatus.SC_UNAUTHORIZED) {
                token = null;
                EntityUtils.consumeQuietly(response.getEntity());
                request.reset();
                connect();
                setRequiredHeaders(request);
                response = httpClient.execute(request);                
                status = response.getStatusLine().getStatusCode();
            }
            if (status != expectedStatus) {
                token = null;
                throw new ClientException(getErrorMessage(getResponseBodyAsString(response)), status);
            }            
        }

        return response;
    }
    
    private HttpClient createHttpClient() {
        try {
            SSLSocketFactory sslSocketFactory = new SSLSocketFactory(new TrustStrategy() {

                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
                
            }, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(
                    new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
            registry.register(
                    new Scheme("https", 443, sslSocketFactory));

            ClientConnectionManager ccm = new PoolingClientConnectionManager(registry);

            return new DefaultHttpClient(ccm);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }
}
