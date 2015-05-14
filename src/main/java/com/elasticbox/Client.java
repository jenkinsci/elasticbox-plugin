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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.*;
import javax.net.ssl.SSLContext;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
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
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;


/**
 *
 * @author Phong Nguyen Le
 */
public class Client {
    private static final String UTF_8 = "UTF-8";

    public static final String BASE_ELASTICBOX_SCHEMA = "http://elasticbox.net/schemas/";
    private static final String DEPLOYMENT_REQUEST_SCHEMA_NAME = "deploy-instance-request";
    private static final String ELASTICBOX_RELEASE = "3";

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
        String SNAPSHOT = "snapshot";
    }
    
    public static final Set FINISH_STATES = new HashSet(Arrays.asList(InstanceState.DONE, InstanceState.UNAVAILABLE));
    public static final Set SHUTDOWN_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.SHUTDOWN, InstanceOperation.SHUTDOWN_SERVICE));
    public static final Set TERMINATE_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.TERMINATE, InstanceOperation.TERMINATE_SERVICE));
    public static final Set ON_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.DEPLOY, InstanceOperation.POWERON, InstanceOperation.REINSTALL, InstanceOperation.RECONFIGURE, InstanceOperation.SNAPSHOT));
    public static final Set OFF_OPERATIONS = new HashSet(Arrays.asList(InstanceOperation.SHUTDOWN, InstanceOperation.SHUTDOWN_SERVICE, InstanceOperation.TERMINATE, InstanceOperation.TERMINATE_SERVICE));
    
    public static interface ProviderState {
        String INITIALIZING = "initializing";
        String PROCESSING = "processing";
        String READY = "ready";
        String DELETING = "deleting";
        String UNAVAILABLE = "unavailable";
    }
    private static final Set<String> PROVIDER_FINISH_STATES = new HashSet<String>(Arrays.asList(ProviderState.READY, ProviderState.UNAVAILABLE));
    
    
    private static HttpClient httpClient = null;

    public static String getSchemaVersion(String url) {
        int index = url.indexOf('/', BASE_ELASTICBOX_SCHEMA.length());
        String schemaVersion = "";
        if (index != -1) {
            schemaVersion = url.substring(BASE_ELASTICBOX_SCHEMA.length(), index);

            if (!schemaVersion.matches("\\d{4}-\\d{2}-\\d{2}")) {
                schemaVersion = "";
            }
        }

        return schemaVersion;
    }

    private final String endpointUrl;
    private final String username;
    private final String password;
    private String token = null;

    protected Client(String endpointUrl, String username, String password, String token) {
        getHttpClient();
        this.endpointUrl = endpointUrl.endsWith("/") ? endpointUrl.substring(0, endpointUrl.length() - 1) : endpointUrl;
        this.username = username;
        this.password = password;
        this.token = token;
    }

    public Client(String endpointUrl, String username, String password) {
        this(endpointUrl, username, password, null);
    }
    
    public Client(String endpointUrl, String token) {
        this(endpointUrl, null, null, token);
    }
    
    public String getEndpointUrl() {
        return endpointUrl;
    }

    protected String getUsername() {
        return username;
    }

    protected String getPassword() {
        return password;
    }

    public void connect() throws IOException {
        if (token != null && username == null) {
            doGet("/services/workspaces", true);
            return;
        }
        
        HttpPost post = new HttpPost(MessageFormat.format("{0}/services/security/token", endpointUrl));
        JSONObject json = new JSONObject();
        json.put("email", getUsername());
        json.put("password", getPassword());
        post.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));
        HttpResponse response = httpClient.execute(post);
        int status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_OK) {
            throw new ClientException(MessageFormat.format("Error {0} connecting to ElasticBox at {1}: {2}", status, 
                    this.endpointUrl, getErrorMessage(getResponseBodyAsString(response))), status);
        }
        token = getResponseBodyAsString(response);            
    }

    public String generateToken(String description) throws IOException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("description", description);
        JSONObject tokenInfo = doPost("/services/tokens", requestBody);
        return tokenInfo.getString("value");
    }
    
    public JSONArray getTokens() throws IOException {
        return (JSONArray) doGet("/services/tokens", true);
    }
    
    public JSONArray getWorkspaces() throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces", endpointUrl), true);
    }
    
    public JSONArray getBoxes(String workspaceId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces/{1}/boxes", endpointUrl, URLEncoder.encode(workspaceId, UTF_8)), true);
    }
    
    public JSONArray getBoxVersions(String boxId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("{0}/services/boxes/{1}/versions", endpointUrl, boxId), true);
    }

    public JSONObject getBox(String boxId) throws IOException {
        return (JSONObject) doGet(MessageFormat.format("{0}/services/boxes/{1}", endpointUrl, boxId), false);
    }

    private JSONObject uploadFile(URI fileUri, ContentType contentType) throws IOException {
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create().setLaxMode();
        if (fileUri.getScheme().equalsIgnoreCase("file")) {
            File file = new File(fileUri);
            if (contentType == null) {
                String mimeType = Files.probeContentType(FileSystems.getDefault().getPath(file.getPath()));
                contentType = mimeType != null ? ContentType.create(mimeType) : ContentType.DEFAULT_BINARY;
            }
            entityBuilder.addBinaryBody("blob", file, contentType, file.getName());
        } else {
            URL fileUrl = fileUri.toURL();
            URLConnection connection = fileUrl.openConnection();
            if (contentType == null) {
                String mimeType = connection.getContentType();
                contentType = mimeType != null ? ContentType.create(mimeType) : ContentType.DEFAULT_BINARY;
            }
            String[] segments = fileUrl.getPath().split("/");
            entityBuilder.addBinaryBody("blob", connection.getInputStream(), contentType, segments[segments.length - 1]);
        }
        HttpPost post = new HttpPost(prepareUrl("/services/blobs/upload"));
        post.setEntity(entityBuilder.build());
        try {
            HttpResponse response = execute(post);
            return JSONObject.fromObject(getResponseBodyAsString(response));
        } finally {
            post.reset();
        }
    }
    
    private String getSchemaVersion() throws IOException {
        JSONArray workspaces = (JSONArray) doGet("/services/workspaces", true);
        return getSchemaVersion(workspaces.getJSONObject(0).getString("schema"));
    }
    
    public JSONObject createWorkspace(String name) throws IOException {
        JSONObject workspace = new JSONObject();
        workspace.put("name", name);
        String schemaVersion = getSchemaVersion();
        String messageFormat = StringUtils.isBlank(schemaVersion) ? "{0}{1}{2}" : "{0}{1}/{2}";

        String schema = MessageFormat.format(messageFormat, BASE_ELASTICBOX_SCHEMA, schemaVersion, "workspaces/team");
        workspace.put("schema", schema);
        return doPost("/services/workspaces", workspace);
    }

    public JSONObject createBox(JSONObject box) throws IOException, URISyntaxException {
        // upload files
        if (box.containsKey("variables")) {
            for (Object variable : box.getJSONArray("variables")) {
                JSONObject variableJson = (JSONObject) variable;
                if (variableJson.getString("type").equals("File")) {
                    uploadFileVariable(variableJson);
                }
            }
        }
        if (box.containsKey("events")) {
            JSONObject events = box.getJSONObject("events");
            for (Object entry : events.entrySet()) {
                Map.Entry mapEntry = (Map.Entry) entry;
                JSONObject blobInfo = uploadFile(new URI(mapEntry.getValue().toString()), ContentType.TEXT_PLAIN);
                JSONObject event = new JSONObject();
                event.put("url", blobInfo.getString("url"));
                event.put("destination_path", "scripts");
                event.put("content_type", "text/x-shellscript");
                events.put(mapEntry.getKey().toString(), event);
            }
        }
        return doPost("/services/boxes", box);
    } 
    
    public IProgressMonitor createProvider(JSONObject provider) throws IOException {
        provider = doPost("/services/providers", provider);
        return new ProviderProgressMonitor(endpointUrl + provider.getString("uri"), provider.getString("updated"));
    }
    
    private boolean canChange(String workspaceId, String boxId) throws IOException {
        JSONObject box = (JSONObject) doGet(MessageFormat.format("{0}/services/boxes/{1}", endpointUrl, boxId), false);
        if (workspaceId.equals(box.getString("owner"))) {
            return true;
        }
        
        for (Object json : box.getJSONArray("members")) {
            JSONObject member = (JSONObject) json;
            if (workspaceId.equals(member.getString("workspace")) && "collaborator".equals(member.getString("role"))) {
                return true;
            }
        }
        
        return false;
    }
    
    public JSONArray getProfiles(String workspaceId, String boxId) throws IOException {
        if (StringUtils.isBlank(workspaceId)) {
            throw new IOException("workspaceId cannot be blank");
        }

        JSONArray profiles = (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces/{1}/profiles?box_version={2}", endpointUrl, URLEncoder.encode(workspaceId, UTF_8), boxId), true);
        if (!canChange(workspaceId, boxId)) {
            // this is a read-only box that could have profiles associated with the its versions
            JSONArray versions = getBoxVersions(boxId);
            if (!versions.isEmpty()) {
                Set<String> versionIDs = new HashSet<String>();
                for(Object version : versions) {
                    versionIDs.add(((JSONObject) version).getString("id"));
                }          

                JSONArray allProfiles = (JSONArray) doGet(MessageFormat.format("{0}/services/workspaces/{1}/profiles", endpointUrl, URLEncoder.encode(workspaceId, UTF_8), boxId), true);
                for (Object json : allProfiles) {
                    JSONObject profile = (JSONObject) json;
                    if (versionIDs.contains(profile.getJSONObject("box").getString("version"))) {
                        profiles.add(profile);
                    }
                }
            }
        }
        
        return profiles;
    }
    
    public JSONObject getInstance(String instanceId) throws IOException {
        if (StringUtils.isBlank(instanceId)) {
            throw new IOException("instanceId cannot be blank");
        }
        return (JSONObject) doGet(MessageFormat.format("{0}/services/instances/{1}", endpointUrl, instanceId), false);
    }
    
    public JSONObject getService(String instanceId) throws IOException {
        return (JSONObject) doGet(MessageFormat.format("/services/instances/{0}/service", instanceId), false);
    }

    public JSONObject getProfile(String profileId) throws IOException {
        if (StringUtils.isBlank(profileId)) {
            throw new IOException("profileId cannot be blank");
        }
        return (JSONObject) doGet(MessageFormat.format("{0}/services/profiles/{1}", endpointUrl, profileId), false);  
    }
    
    public JSONArray getInstances(String workspaceId) throws IOException, IOException, IOException, IOException, IOException {
        if (StringUtils.isBlank(workspaceId)) {
            throw new IOException("workspaceId cannot be blank");
        }
        return (JSONArray) doGet(MessageFormat.format("/services/workspaces/{0}/instances", workspaceId), true);
    }
    
    public JSONArray getInstances(String workspaceId, List<String> instanceIDs) throws IOException {
        if (StringUtils.isBlank(workspaceId)) {
            throw new IOException("workspaceId cannot be blank");
        }

        JSONArray instances = new JSONArray();
        for (int start = 0; start < instanceIDs.size();) {
            int end = Math.min(800, instanceIDs.size());            
            StringBuilder ids = new StringBuilder();
            for (int i = start; i < end; i++) {
                ids.append(instanceIDs.get(i)).append(',');
            }
            instances.addAll((JSONArray) doGet(MessageFormat.format("/services/workspaces/{0}/instances?ids={1}", workspaceId, ids.toString()), true));
            start = end;
        }

        return instances;
    }
    
    public JSONArray getInstances(List<String> instanceIDs) throws IOException {
        JSONArray instances = new JSONArray();
        Set<String> fetchedInstanceIDs = new HashSet<String>();
        JSONArray workspaces = getWorkspaces();
        for (Object workspace : workspaces) {
            JSONArray workspaceInstances = getInstances(((JSONObject) workspace).getString("id"), instanceIDs);            
            for (Object instance : workspaceInstances) {
                String instanceId = ((JSONObject) instance).getString("id");
                instanceIDs.remove(instanceId);
                if (!fetchedInstanceIDs.contains(instanceId)) {
                    instances.add(instance);
                    fetchedInstanceIDs.add(instanceId);
                }
            }
            
            if (instanceIDs.isEmpty()) {
                break;
            }
        }
        
        return instances;
    }
    
    public JSONArray getBoxStack(String boxId) throws IOException {
        return (JSONArray) doGet(MessageFormat.format("/services/boxes/{0}/stack", boxId), true);
    }
    
    public String getLatestBoxVersion(String workspace, String boxId) throws IOException {
        JSONObject boxJson = getBox(boxId);
        boolean canWrite = false;
        if (boxJson.getString("owner").equals(workspace)) {
            canWrite = true;
        } else {
            Set<String> collaborators = new HashSet<String>();
            for (Object member : boxJson.getJSONArray("members")) {
                JSONObject memberJson = (JSONObject) member;
                if (memberJson.getString("role").equals("collaborator")) {
                    collaborators.add(memberJson.getString("workspace"));
                }
            }
            canWrite = collaborators.contains(workspace);
        }       
        String boxVersion;
        if (canWrite) {            
            boxVersion = boxId;
        } else {
            JSONArray boxVersions = getBoxVersions(boxId);
            if (boxVersions.isEmpty()) {
                throw new IOException(MessageFormat.format("Box ''{0}'' does not have any version.", boxJson.getString("name")));
            } else {
                boxVersion = boxVersions.getJSONObject(0).getString("id");
            }
        }        
        return boxVersion;
    }
    
    public JSONObject updateInstance(JSONObject instance) throws IOException {
        HttpPut put = new HttpPut(getInstanceUrl(instance.getString("id")));
        put.setEntity(new StringEntity(instance.toString(), ContentType.APPLICATION_JSON));
        try {
            HttpResponse response = execute(put);
            return JSONObject.fromObject(getResponseBodyAsString(response));
        } finally {
            put.reset();
        }                
    }
    
    public JSONObject updateInstance(JSONObject instance, JSONArray variables) throws IOException  {
        if (variables != null && !variables.isEmpty()) {
            JSONArray instanceBoxes = instance.getJSONArray("boxes");
            JSONObject mainBox = instanceBoxes.getJSONObject(0);
            JSONArray boxStack = new BoxStack(mainBox.getString("id"), instanceBoxes, this).toJSONArray();
            JSONArray boxVariables = new JSONArray();
            for (Object box : boxStack) {
                boxVariables.addAll(((JSONObject) box).getJSONArray("variables"));
            }
            JSONArray instanceVariables = instance.getJSONArray("variables");
            List<JSONObject> newVariables = new ArrayList<JSONObject>();
            for (Object variable : variables) {
                JSONObject variableJson = (JSONObject) variable;
                JSONObject instanceVariable = findVariable(variableJson, instanceVariables);            
                if (instanceVariable == null) {
                    JSONObject boxVariable = findVariable(variableJson, boxVariables);
                    if (boxVariable != null) {
                        instanceVariable = JSONObject.fromObject(boxVariable);
                        if (instanceVariable.getString("scope").isEmpty()) {
                            instanceVariable.remove("scope");
                        }
                        newVariables.add(instanceVariable);
                    }
                }
                if (instanceVariable != null) {
                    if ("File".equals(variableJson.getString("type"))) {
                        uploadFileVariable(variableJson);
                    }
                    instanceVariable.put("value", variableJson.getString("value"));
                }
            }
            instanceVariables.addAll(newVariables);
            instance.put("variables", instanceVariables);
        }
        
        return updateInstance(instance);
    }
    
    public JSONObject updateInstance(JSONObject instance, JSONArray variables, String boxVersion) throws IOException  {
        JSONArray variablesWithFullScope = new JSONArray();        
        if (variables != null && !variables.isEmpty()) {
            JSONArray instanceBoxes = instance.getJSONArray("boxes");
            JSONObject mainBox = instanceBoxes.getJSONObject(0);
            BoxStack boxStack = new BoxStack(mainBox.getString("id"), instanceBoxes, this);
            JSONArray stackBoxes = boxStack.toJSONArray();            
            JSONObject boxVersionJson = boxStack.findBox(boxVersion);
            if (boxVersionJson == null) {
                throw new IOException(MessageFormat.format("Instance {0} does not have box version {1}", instance.getString("id"), boxVersion));                
            }
            boxVersion = boxVersionJson.getString("id");
            JSONArray boxVariables = null;
            for (Object box : stackBoxes) {
                JSONObject boxJson = (JSONObject) box;
                if (boxJson.getString("id").equals(boxVersion)) {
                    boxVariables = boxJson.getJSONArray("variables");
                    break;
                }
            }
            if (boxVariables == null) {
                throw new IOException(MessageFormat.format("Instance {0} does not have box version {1} in its runtime stack.", instance.getString("id"), boxVersion));
            }
            if (!boxVariables.isEmpty()) {
                String boxScope = boxVariables.getJSONObject(0).getString("scope");
                for (Object variable : variables) {
                    JSONObject variableWithFullScope = JSONObject.fromObject(variable);
                    String scope = variableWithFullScope.containsKey("scope") ? variableWithFullScope.getString("scope") : StringUtils.EMPTY;
                    if (!StringUtils.isBlank(boxScope)) {
                        scope = StringUtils.isBlank(scope) ? boxScope : boxScope + '.' + scope;
                    }
                    variableWithFullScope.put("scope", scope);
                    variablesWithFullScope.add(variableWithFullScope);
                }
            } else if (!variables.isEmpty()) {
                throw new IOException(MessageFormat.format("Box version {0} doesn't have any variable to update", boxVersion));
            }
        }
        
        return updateInstance(instance, variablesWithFullScope);
    }    
    
    private void uploadFileVariable(JSONObject fileVariable) throws IOException {
        String value = fileVariable.getString("value");
        if (StringUtils.isBlank(value)) {
            return;
        }
        URI fileUri;
        try {
            fileUri = new URI(value);
        } catch (URISyntaxException ex) {
            throw new IOException(MessageFormat.format("Invalid file URI specified for variable {0}: {1}", 
                    fileVariable.getString("name"), value), ex);
        }
        JSONObject blobInfo = uploadFile(fileUri, null);
        fileVariable.put("value", blobInfo.getString("url"));
    }

    public JSONObject updateBox(String boxId, JSONArray variables) throws IOException {
        String boxUrl = MessageFormat.format("/services/boxes/{0}", boxId);
        JSONObject box = (JSONObject) doGet(boxUrl, false);
        if (box.containsKey("version")) {
            throw new IOException("Cannot update a box version");
        }
        
        if (variables != null && !variables.isEmpty()) {
            JSONArray boxStack = new BoxStack(boxId, getBoxStack(boxId), this).toJSONArray();
            JSONArray boxVariables = new JSONArray();
            for (Object stackBox : boxStack) {
                boxVariables.addAll(((JSONObject) stackBox).getJSONArray("variables"));
            }
            JSONArray existingVariables = box.getJSONArray("variables");
            List<JSONObject> newVariables = new ArrayList<JSONObject>();
            for (Object variable : variables) {
                JSONObject variableJson = (JSONObject) variable;
                JSONObject instanceVariable = findVariable(variableJson, existingVariables);            
                if (instanceVariable == null) {
                    JSONObject boxVariable = findVariable(variableJson, boxVariables);
                    if (boxVariable != null) {
                        instanceVariable = JSONObject.fromObject(boxVariable);
                        if (instanceVariable.getString("scope").isEmpty()) {
                            instanceVariable.remove("scope");
                        }
                        newVariables.add(instanceVariable);
                    }
                }
                if (instanceVariable != null) {
                    if ("File".equals(variableJson.getString("type"))) {
                        uploadFileVariable(variableJson);
                    }
                    instanceVariable.put("value", variableJson.getString("value"));
                }
            }
            existingVariables.addAll(newVariables);
            box.put("variables", existingVariables);
        }
        
        return doUpdate(boxUrl, box);
    }
    
    public String getBoxPageUrl(String boxId) {
        return getPageUrl(endpointUrl, MessageFormat.format("/services/boxes/{0}", boxId));
    }    
    
    protected abstract class ProgressMonitor extends AbstractProgressMonitor {
        protected final String lastModified;

        protected ProgressMonitor(String resourceUrl, String lastModified) {
            super(resourceUrl);
            this.lastModified = lastModified;            
        }

        @Override
        protected JSONObject getResource() throws IOException, IncompleteException {
            try {
                return (JSONObject) doGet(getResourceUrl(), false);
            } catch (ClientException ex) {
                if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                    throw new IncompleteException(MessageFormat.format("{0} cannot be found", getResourceUrl()));
                } else {
                    throw ex;
                }                
            }            
        }

    }

    protected class InstanceProgressMonitor extends ProgressMonitor {
        private final Set<String> operations;
        
        private InstanceProgressMonitor(String instanceUrl, Set<String> operations, String lastModified) {
            super(instanceUrl, lastModified);
            this.operations = operations;
        }
        
        public boolean isDone(JSONObject instance) throws IProgressMonitor.IncompleteException, IOException {
            String updated = instance.getString("updated");
            String state = instance.getString("state");
            String operation = instance.getString("operation");
            if (lastModified.equals(updated) || !FINISH_STATES.contains(state)) {
                return false;
            }

            if (state.equals(InstanceState.UNAVAILABLE)) {
                throw new IProgressMonitor.IncompleteException(MessageFormat.format("The instance at {0} is unavailable", getPageUrl(endpointUrl, instance)));
            } 

            if (operations != null && !operations.contains(operation)) {
                throw new IProgressMonitor.IncompleteException(MessageFormat.format("Unexpected operation ''{0}'' has been performed for instance {1}", operation, getPageUrl(endpointUrl, instance)));
            }
            
            return true;
        }

    }
    
    protected class ProviderProgressMonitor extends ProgressMonitor {

        public ProviderProgressMonitor(String resourceUrl, String lastModified) {
            super(resourceUrl, lastModified);
        }

        public boolean isDone(JSONObject instance) throws IncompleteException, IOException {
            String updated = instance.getString("updated");
            String state = instance.getString("state");
            if (lastModified.equals(updated) || !PROVIDER_FINISH_STATES.contains(state)) {
                return false;
            }
            if (state.equals(InstanceState.UNAVAILABLE)) {
                throw new IProgressMonitor.IncompleteException(MessageFormat.format("The instance at {0} is unavailable", getResourceUrl()));
            } 
            return true;            
        }
        
    }
    
    public IProgressMonitor deploy(String profileId, String workspaceId, String environment, int instances, JSONArray variables) throws IOException {
        return deploy(null, profileId, workspaceId, environment, instances, variables, null, null);
    }
    
    public IProgressMonitor deploy(String boxVersion, String profileId, String workspaceId, String environment, 
            int instances, JSONArray variables, String expirationTime, String expirationOperation) throws IOException {        
        JSONObject profile = (JSONObject) doGet(MessageFormat.format("/services/profiles/{0}", profileId), false);
        JSONObject deployRequest = new JSONObject();
        
        String profileSchema = profile.getString("schema");
        String schemaVersion = getSchemaVersion(profileSchema);
        String messageFormat = StringUtils.isBlank(schemaVersion) ? "{0}{1}{2}" : "{0}{1}/{2}";
        if (StringUtils.isBlank(schemaVersion) || schemaVersion.compareTo("2014-05-23") > 0) {
            if (boxVersion != null) {
                profile.getJSONObject("box").put("version", boxVersion);
            }
            
            JSONObject serviceProfile = profile.getJSONObject("profile");
            if (serviceProfile.containsKey("instances")) {
                serviceProfile.put("instances", instances);
            }
            deployRequest.put("schema", MessageFormat.format(messageFormat, BASE_ELASTICBOX_SCHEMA, schemaVersion, DEPLOYMENT_REQUEST_SCHEMA_NAME));
            for (Object json : variables) {
                JSONObject variable = (JSONObject) json;
                if (variable.containsKey("scope") && variable.getString("scope").isEmpty()) {
                    variable.remove("scope");
                }
                if ("File".equals(variable.getString("type"))) {
                    uploadFileVariable(variable);
                }
            }
            deployRequest.put("variables", variables);
            
            if (expirationTime != null && expirationOperation != null && (StringUtils.isBlank(schemaVersion) || schemaVersion.compareTo("2014-10-09") >= 0)) {
                JSONObject lease = new JSONObject();
                lease.put("expire", expirationTime);
                lease.put("operation", expirationOperation);
                deployRequest.put("lease", lease);
            }
        } else {
            JSONObject mainInstance = (JSONObject) profile.getJSONArray("instances").get(0);
            JSONArray jsonVars = mainInstance.getJSONArray("variables");
            for (Object json : variables) {
                JSONObject variable = (JSONObject) json;
                JSONObject jsonVar = findVariable(variable, jsonVars);
                if (jsonVar == null) {
                    jsonVars.add(variable);
                } else {
                    jsonVar.put("value", variable.getString("value"));
                }
            }
            JSONObject serviceProfile = mainInstance.getJSONObject("profile");
            if (serviceProfile.containsKey("instances")) {
                serviceProfile.put("instances", instances);
            }
            deployRequest.put("schema", MessageFormat.format(messageFormat, BASE_ELASTICBOX_SCHEMA, schemaVersion, "deploy-service-request"));
        }
        deployRequest.put("environment", environment);
        deployRequest.put("profile", profile);
        deployRequest.put("owner", workspaceId);        
        
        JSONObject instance = doPost("/services/instances", deployRequest);
        return new InstanceProgressMonitor(endpointUrl + instance.getString("uri"), 
                Collections.singleton(InstanceOperation.DEPLOY), instance.getString("updated"));
    }

    public IProgressMonitor reconfigure(String instanceId, JSONArray variables) throws IOException {
        JSONObject instance = doOperation(instanceId, InstanceOperation.RECONFIGURE, variables);
        return new InstanceProgressMonitor(getInstanceUrl(instanceId), Collections.singleton(InstanceOperation.RECONFIGURE),
                instance.getString("updated"));        
    }
    
    private JSONObject doOperation(String instanceId, String operation, JSONArray variables) throws IOException {
        return doOperation(getInstance(instanceId), operation, variables);
    }
        
    private JSONObject doOperation(JSONObject instance, String operation, JSONArray variables) throws IOException {
        String instanceId = instance.getString("id");
        String instanceUrl = getInstanceUrl(instanceId);
        if (variables != null && !variables.isEmpty()) {
            updateInstance(instance, variables);
        }
        
        HttpPut put = new HttpPut(MessageFormat.format("{0}/{1}", instanceUrl, operation));
        try {
            execute(put);
            return getInstance(instanceId);
        } finally {
            put.reset();
        }
    }
    
    private IProgressMonitor doTerminate(String instanceUrl, String operation) throws IOException {
        JSONObject instance = (JSONObject) doGet(instanceUrl, false);
        HttpDelete delete = new HttpDelete(MessageFormat.format("{0}?operation={1}", instanceUrl, operation));
        try {
            execute(delete);
            return new InstanceProgressMonitor(instanceUrl, TERMINATE_OPERATIONS, instance.getString("updated"));
        } finally {
            delete.reset();
        }        
    }
    
    public IProgressMonitor terminate(String instanceId) throws IOException {
        String instanceUrl = getInstanceUrl(instanceId);
        JSONObject instance = (JSONObject) doGet(instanceUrl, false);
        String state = instance.getString("state");  
        String operation = instance.getString("operation");
        String terminateOperation = (state.equals(InstanceState.DONE) && ON_OPERATIONS.contains(operation)) ||
                (state.equals(InstanceState.UNAVAILABLE) && operation.equals(InstanceOperation.TERMINATE))? 
                "terminate" : "force_terminate";
        return doTerminate(instanceUrl, terminateOperation);
    }
    
    public IProgressMonitor forceTerminate(String instanceId) throws IOException {
        return doTerminate(getInstanceUrl(instanceId), "force_terminate");
    }
    
    public IProgressMonitor poweron(String instanceId) throws IOException {
        JSONObject instance = getInstance(instanceId);
        String state = instance.getString("state");
        if (ON_OPERATIONS.contains(instance.getString("operation")) && (InstanceState.DONE.equals(state) || InstanceState.PROCESSING.equals(state))) {
            return new IProgressMonitor.DoneMonitor(getInstanceUrl(instanceId));
        }
        
        instance = doOperation(instance, InstanceOperation.POWERON, null);
        return new InstanceProgressMonitor(getInstanceUrl(instanceId), Collections.singleton(InstanceOperation.POWERON),
            instance.getString("updated"));
    }

    public IProgressMonitor shutdown(String instanceId) throws IOException {
        JSONObject instance = doOperation(instanceId, InstanceOperation.SHUTDOWN, null);
        return new InstanceProgressMonitor(getInstanceUrl(instanceId), SHUTDOWN_OPERATIONS, instance.getString("updated"));
    }

    public void delete(String instanceId) throws IOException {
        doDelete(MessageFormat.format("{0}?operation=delete", getInstanceUrl(instanceId)));
    }

    public IProgressMonitor reinstall(String instanceId, JSONArray variables) throws IOException {
        JSONObject instance = doOperation(instanceId, InstanceOperation.REINSTALL, variables);
        return new InstanceProgressMonitor(getInstanceUrl(instanceId), Collections.singleton(InstanceOperation.REINSTALL),
            instance.getString("updated"));
    }
    
    private String prepareUrl (String url) {
        return url.startsWith("/") ? endpointUrl + url : url;
    }
        
    public JSON doGet(String url, boolean isArray) throws IOException {
        HttpGet get = new HttpGet(prepareUrl(url));
        try {
            HttpResponse response = execute(get);
            return isArray ? JSONArray.fromObject(getResponseBodyAsString(response)) : JSONObject.fromObject(getResponseBodyAsString(response));                    
        } finally {
            get.reset();
        }
    }

    public JSONObject doPost(String url, JSONObject resource) throws IOException {
        HttpPost post = new HttpPost(prepareUrl(url));
        post.setEntity(new StringEntity(resource.toString(), ContentType.APPLICATION_JSON));
        try {
            HttpResponse response = execute(post);
            return JSONObject.fromObject(getResponseBodyAsString(response));
        } finally {
            post.reset();
        }                        
    }

    public JSONObject doUpdate(String url, JSONObject resource) throws IOException {
        HttpPut put = new HttpPut(prepareUrl(url));
        put.setEntity(new StringEntity(resource.toString(), ContentType.APPLICATION_JSON));
        try {
            HttpResponse response = execute(put);
            return JSONObject.fromObject(getResponseBodyAsString(response));
        } finally {
            put.reset();
        }                        
    }
    
    public void writeTo(String url, OutputStream output) throws IOException {
        HttpGet get = new HttpGet(prepareUrl(url));
        try {
            HttpResponse response = execute(get);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                entity.writeTo(output);
            }
        } finally {
            get.reset();
        }        
    }
    
    public void doDelete(String url) throws IOException {
        HttpDelete delete = new HttpDelete(prepareUrl(url));
        HttpResponse response = null;
        try {
            response = execute(delete);
        } finally {
            delete.reset();
            if (response != null && response.getEntity() != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }        
    }
    
    public String getInstanceUrl(String instanceId) {
        return getInstanceUrl(endpointUrl, instanceId);
    }
    
    
    public static final String getInstanceUrl(String endpointUrl, String instanceId) {
        return MessageFormat.format("{0}/services/instances/{1}", endpointUrl, instanceId);        
    }
    
    public static final String getInstancePageUrl(String endpointUrl, String instanceId) {
        return getPageUrl(endpointUrl, getInstanceUrl(endpointUrl, instanceId));
    }
    
    public static final String getResourceId(String resourceUrl) {
        return resourceUrl != null ? resourceUrl.substring(resourceUrl.lastIndexOf('/') + 1) : null;
    }
    
    public static final String getPageUrl(String endpointUrl, String resourceUrl) {
        String resourceId = getResourceId(resourceUrl);
        if (resourceId != null) {
            if (resourceUrl.startsWith(MessageFormat.format("{0}/services/instances/", endpointUrl))) {
                return MessageFormat.format("{0}/#/instances/{1}/i", endpointUrl, resourceId);
            } else if (resourceUrl.startsWith(MessageFormat.format("{0}/services/boxes/", endpointUrl))) {
                return MessageFormat.format("{0}/#/boxes/{1}/b", endpointUrl, resourceId);
            }
        }
        return null;
    }
    
    public static final String getPageUrl(String endpointUrl, JSONObject resource) {
        String resourceUri = resource.getString("uri");
        if (resourceUri.startsWith("/services/instances/")) {
            return MessageFormat.format("{0}/#/instances/{1}/{2}", endpointUrl, resource.getString("id"),
                    dasherize(resource.getString("name").toLowerCase()));
        } else if (resourceUri.startsWith("/services/boxes")) {
            return MessageFormat.format("{0}/#/boxes/{1}/{2}", endpointUrl, resource.getString("id"),
                    dasherize(resource.getString("name").toLowerCase()));            
        }
        return null;
    }
    
    private static String dasherize(String str) {
        return str.replaceAll("[^a-z0-9-]", "-");
    }

    private JSONObject findVariable(JSONObject variable, JSONArray variables) {
        String name = variable.getString("name");
        String scope = variable.containsKey("scope") ? variable.getString("scope") : StringUtils.EMPTY;
        for (Object var : variables) {
            JSONObject json = (JSONObject) var;
            if (json.getString("name").equals(name)) {
                String varScope = json.containsKey("scope") ? json.getString("scope") : StringUtils.EMPTY;
                if (scope.equals(varScope)) {
                    return json;
                } 
            }
        }
        return null;
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
        request.setHeader("ElasticBox-Token", token);
        request.setHeader("ElasticBox-Release", ELASTICBOX_RELEASE);
    }
    
    public static String getResponseBodyAsString(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        return entity != null ? EntityUtils.toString(entity) : null;
    }
    
    protected HttpResponse execute(HttpRequestBase request) throws IOException {
        if (token == null) {
            connect();
        }
        setRequiredHeaders(request);
        HttpResponse response = httpClient.execute(request);
        int status = response.getStatusLine().getStatusCode();
        if (status == HttpStatus.SC_UNAUTHORIZED) {
            if (username != null) {
                token = null;
                EntityUtils.consumeQuietly(response.getEntity());
                request.reset();
                connect();
                setRequiredHeaders(request);
                response = httpClient.execute(request);                
            }
            status = response.getStatusLine().getStatusCode();
        }
        if (status < 200 || status > 299) {
            if (username != null) {
                token = null;
            }
            throw new ClientException(getErrorMessage(getResponseBodyAsString(response)), status);
        }            

        return response;
    }

    public static final HttpClient createHttpClient() throws Exception {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
//        clientBuilder.setUserAgent("jenkins/elasticbox");
        clientBuilder.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        final SSLContextBuilder contextBuilder = SSLContexts.custom();
        contextBuilder.loadTrustMaterial(null, new TrustStrategy() {

            public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                return true;
            }
            
        });
        SSLContext sslContext = contextBuilder.build();
        clientBuilder.setSslcontext(sslContext);
        clientBuilder.setConnectionManager(new PoolingHttpClientConnectionManager());
        return clientBuilder.build();        
    }
        
    public static synchronized HttpClient getHttpClient() {
        if (httpClient == null) {            
//            try {
//                httpClient = createHttpClient();
//            } catch (Exception e) {
//                httpClient = HttpClientBuilder.create().build();
//            }
            
            try {
                SSLSocketFactory sslSocketFactory = new SSLSocketFactory(new TrustStrategy() {

                    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        return true;
                    }

                }, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

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
