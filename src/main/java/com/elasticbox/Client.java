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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    
    public static interface InstanceOperation {
        String DEPLOY = "deploy";
        String REINSTALL = "reinstall";
        String RECONFIGURE = "reconfigure";
        String POWERON = "poweron";
        String SHUTDOWN = "shutdown";
        String SHUTDOWN_SERVICE = "shutdown_service";
        String TERMINATE = "terminate";
        String TERMINATE_SERVICE = "terminate_service";
    }
    
    public static final Set FINISH_STATES = new HashSet(Arrays.asList(InstanceState.DONE, InstanceState.UNAVAILABLE));
    public static final Set SHUTDOWN_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.SHUTDOWN, InstanceOperation.SHUTDOWN_SERVICE));
    public static final Set TERMINATE_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.TERMINATE, InstanceOperation.TERMINATE_SERVICE));
    public static final Set ON_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.DEPLOY, InstanceOperation.POWERON, InstanceOperation.REINSTALL, InstanceOperation.RECONFIGURE));
    public static final Set OFF_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.SHUTDOWN, InstanceOperation.SHUTDOWN_SERVICE, InstanceOperation.TERMINATE, InstanceOperation.TERMINATE_SERVICE));
    
    private static HttpClient httpClient = null;
    
    private static String getSchemaVersion(String url) {
        return url.substring(BASE_ELASTICBOX_SCHEMA.length(), url.indexOf('/', BASE_ELASTICBOX_SCHEMA.length()));
    }

    private final String endpointUrl;
    private final String username;
    private final String password;
    private String token = null;

    public Client(String endpointUrl, String username, String password) {
        createHttpClient();
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
    
    public JSONArray getInstances(String workspaceId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("/services/workspaces/{0}/instances", workspaceId), true);
    }
    
    /**
     * Returns only the variables of main box for now.
     * 
     * @param profileId
     * @return
     * @throws IOException 
     */
    public JSONArray getProfileVariables(String profileId) throws IOException {
        JSONObject profile = getProfile(profileId);
        String boxId = profile.getJSONObject("box").getString("version");
        JSONObject box = (JSONObject) doGet(MessageFormat.format("/services/boxes/{0}", boxId), false);
        return box.getJSONArray("variables");
    }    
    
    protected class ProgressMonitor implements IProgressMonitor {
        private final String instanceUrl;
        private final long creationTime;
        private final Object waitLock = new Object();
        private final Set<String> operations;
        
        private ProgressMonitor(String instanceUrl) {
            this(instanceUrl, null);
        }

        private ProgressMonitor(String instanceUrl, Set<String> operations) {
            this.instanceUrl = instanceUrl;
            this.operations = operations;
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
            JSONObject instance = (JSONObject) doGet(instanceUrl, false);
            while(remainingTime > 0 && 
                (!states.contains((state = instance.getString("state"))) || (operations != null && !operations.contains(instance.getString("operation"))))
            ) {
                synchronized(waitLock) {
                    try {
                        waitLock.wait(1000);
                    } catch (InterruptedException ex) {
                    }
                }
                instance = (JSONObject) doGet(instanceUrl, false);
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
                JSONObject jsonVar = findVariable(entry.getKey(), jsonVars);
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
            HttpResponse response = execute(post);
            JSONObject instance = JSONObject.fromObject(getResponseBodyAsString(response));
            return new ProgressMonitor(endpointUrl + instance.getString("uri"), Collections.singleton(InstanceOperation.DEPLOY));
        } finally {
            post.reset();
        }
    }
    
    public IProgressMonitor reconfigure(String instanceId, JSONArray variables) throws IOException {
        JSONObject instance = getInstance(instanceId);
        JSONArray instanceVariables = instance.getJSONArray("variables");
        JSONArray boxVariables = instance.getJSONArray("boxes").getJSONObject(0).getJSONArray("variables");
        List<JSONObject> newVariables = new ArrayList<JSONObject>();
        for (Object variable : variables) {
            JSONObject variableJson = (JSONObject) variable;
            String variableName = variableJson.getString("name");
            JSONObject instanceVariable = findVariable(variableName, instanceVariables);            
            if (instanceVariable == null) {
                JSONObject boxVariable = findVariable(variableName, boxVariables);
                if (boxVariable != null) {
                    instanceVariable = JSONObject.fromObject(boxVariable);
                    newVariables.add(instanceVariable);
                }
            }
            if (instanceVariable != null) {
                instanceVariable.put("value", variableJson.getString("value"));
            }
        }
        instanceVariables.addAll(newVariables);
        instance.put("variables", instanceVariables);
        String instanceUrl = getInstanceUrl(instanceId);
        HttpPut put = new HttpPut(instanceUrl);
        put.setEntity(new StringEntity(instance.toString(), ContentType.APPLICATION_JSON));
        try {
            execute(put);
            put.reset();
            put = new HttpPut(MessageFormat.format("{0}/reconfigure", instanceUrl));
            execute(put);
            return new ProgressMonitor(instanceUrl, Collections.singleton(InstanceOperation.RECONFIGURE));
        } finally {
            put.reset();
        }
    }
    
    public IProgressMonitor terminate(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        ProgressMonitor monitor = new ProgressMonitor(instanceUrl, TERMINATE_OPERATIONS);        
        String state = monitor.getState();
        String operation = state.equals(InstanceState.DONE) ? "terminate" : "force_terminate";
        HttpDelete delete = new HttpDelete(MessageFormat.format("{0}?operation={1}", instanceUrl, operation));
        try {
            execute(delete);
            return monitor;
        } finally {
            delete.reset();
        }
    }
    
    public IProgressMonitor poweron(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        HttpPut put = new HttpPut(MessageFormat.format("{0}/poweron", instanceUrl));
        try {
            execute(put);
            return new ProgressMonitor(instanceUrl, Collections.singleton(InstanceOperation.POWERON));
        } finally {
            put.reset();
        }
    }

    public IProgressMonitor shutdown(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        HttpPut put = new HttpPut(MessageFormat.format("{0}/shutdown", instanceUrl));
        try {
            execute(put);
            return new ProgressMonitor(instanceUrl, SHUTDOWN_OPERATIONS);
        } finally {
            put.reset();
        }
    }

    public void delete(String instanceId) throws IOException {
        HttpDelete delete = new HttpDelete(MessageFormat.format("{0}?operation=delete", getInstanceUrl(instanceId)));
        try {
            execute(delete);
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
            HttpResponse response = execute(get);
            return isArray ? JSONArray.fromObject(getResponseBodyAsString(response)) : JSONObject.fromObject(getResponseBodyAsString(response));                    
        } finally {
            get.reset();
        }
    }
    
    public static final String getResourceId(String resourceUrl) {
        return resourceUrl != null ? resourceUrl.substring(resourceUrl.lastIndexOf('/') + 1) : null;
    }
    
    public static final String getPageUrl(String endpointUrl, String resourceUrl) {
        if (resourceUrl.startsWith(MessageFormat.format("{0}/services/instances/", endpointUrl))) {
            String instanceId = getResourceId(resourceUrl);
            if (instanceId != null) {
                return MessageFormat.format("{0}/#/instances/{1}/i", endpointUrl, instanceId);
            }
        }
        return null;
    }

    private JSONObject findVariable(String name, JSONArray variables) {
        for (Object var : variables) {
            JSONObject json = (JSONObject) var;
            if (json.getString("name").equals(name)) {
                return json;
            }
        }
        return null;
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
        return error != null && error.containsKey("message")? error.getString("message") : errorResponseBody;
    }
    
    private void setRequiredHeaders(HttpRequestBase request) {
        request.setHeader(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE);
        request.setHeader("ElasticBox-Token", token);
    }
    
    private static String getResponseBodyAsString(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        return entity != null ? EntityUtils.toString(entity) : null;
    }
    
    private HttpResponse execute(HttpRequestBase request) throws IOException {
        if (token == null) {
            connect();
        }
        setRequiredHeaders(request);
        HttpResponse response = httpClient.execute(request);
        int status = response.getStatusLine().getStatusCode();
        if (status == HttpStatus.SC_UNAUTHORIZED) {
            token = null;
            EntityUtils.consumeQuietly(response.getEntity());
            request.reset();
            connect();
            setRequiredHeaders(request);
            response = httpClient.execute(request);                
            status = response.getStatusLine().getStatusCode();
        }
        if (status < 200 || status > 299) {
            token = null;
            throw new ClientException(getErrorMessage(getResponseBodyAsString(response)), status);
        }            

        return response;
    }
    
    private static synchronized HttpClient createHttpClient() {
        if (httpClient == null) {
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

                httpClient = new DefaultHttpClient(ccm);
            } catch (Exception e) {
                httpClient = new DefaultHttpClient();
            }
        }
        
        return httpClient;
    }
}
