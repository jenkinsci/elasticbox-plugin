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

package com.elasticbox.jenkins;

import com.elasticbox.BoxStack;
import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.Constants;
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.CompositeObjectFilter;
import com.elasticbox.jenkins.util.JsonUtil;
import com.elasticbox.jenkins.util.ObjectFilter;
import com.elasticbox.jenkins.util.SlaveInstance;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
/**
 *
 * @author Phong Nguyen Le
 */
public class DescriptorHelper {
    private static final Logger LOGGER = Logger.getLogger(DescriptorHelper.class.getName());

    public static final String ANY_BOX = "AnyBox";
    public static final String LATEST_BOX_VERSION = "LATEST";

    public static ListBoxModel getCloudFormationProviders(Client client, String workspace) {
        ListBoxModel model = new ListBoxModel();
        if (client != null && StringUtils.isNotBlank(workspace)) {
            try {
                for (Object providerObject : client.getProviders(workspace)) {
                    JSONObject providerJson = (JSONObject) providerObject;
                    if (providerJson.getString("type").equals(Constants.AMAZON_PROVIDER_TYPE) &&
                            JsonUtil.find(providerJson, "services", "name", Constants.CLOUD_FOUNDATION_SERVICE) != null) {
                        model.add(providerJson.getString("name"), providerJson.getString("id"));
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        return model;
    }

    public static ListBoxModel getCloudFormationLocations(Client client, String provider) {
        ListBoxModel model = new ListBoxModel();
        if (client != null && StringUtils.isNotBlank(provider)) {
            try {
                JSONObject providerJson = client.getProvider(provider);
                JSONObject cloudFormationService = JsonUtil.find(providerJson, "services", "name", Constants.CLOUD_FOUNDATION_SERVICE);
                if (cloudFormationService != null) {
                    for (Object location : cloudFormationService.getJSONArray("locations")) {
                        String locationName = ((JSONObject) location).getString("name");
                        model.add(locationName, locationName);
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        return model;
    }

    public static class JSONArrayResponse implements HttpResponse {
        private final JSONArray jsonArray;

        public JSONArrayResponse(JSONArray jsonArray) {
            this.jsonArray = jsonArray;
        }

        public JSONArray getJsonArray() {
            return jsonArray;
        }

        public void generateResponse(StaplerRequest request, StaplerResponse response, Object node) throws IOException, ServletException {
            response.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            response.getWriter().write(jsonArray.toString());
        }

    }

    public static ListBoxModel getClouds() {
        ListBoxModel clouds = new ListBoxModel();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                clouds.add(cloud.getDisplayName(), cloud.name);
            }
        }

        return clouds;
    }

    public static String getToken(String endpointUrl, String username, String password) throws IOException {
        final String TOKEN_DESCRIPTION = "ElasticBox CI Jenkins Plugin";
        String token = null;
        Client client = new Client(endpointUrl, username, password);
        try {
            token = client.generateToken(TOKEN_DESCRIPTION);
        } catch (ClientException ex) {
            if (ex.getStatusCode() != HttpStatus.SC_CONFLICT) {
                throw ex;
            } else {
                for (Object tokenObject : client.getTokens()) {
                    JSONObject tokenJson = (JSONObject) tokenObject;
                    if (tokenJson.getString("description").equals(TOKEN_DESCRIPTION)) {
                        token = tokenJson.getString("value");
                        break;
                    }
                }
            }
        }
        return token;
    }

    public static ListBoxModel getWorkspaces(String cloud) {
        return getWorkspaces(ClientCache.getClient(cloud));
    }

    public static ListBoxModel getWorkspaces(Client client) {
        ListBoxModel workspaces = new ListBoxModel();
        if (client == null) {
            return workspaces;
        }

        try {
            for (Object workspace : client.getWorkspaces()) {
                JSONObject json = (JSONObject) workspace;
                String displayName = MessageFormat.format("{0} ({1})", json.getString("name"), json.getString("id"));
                workspaces.add(displayName, json.getString("id"));
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching workspaces", ex);
        }

        return sort(workspaces);
    }

    public static ListBoxModel getBoxes(Client client, String workspace) {
        ListBoxModel boxes = new ListBoxModel();
        if (StringUtils.isBlank(workspace) || client == null) {
            return boxes;
        }

        try {
            for (Object box : client.getBoxes(workspace)) {
                JSONObject json = (JSONObject) box;
                boxes.add(json.getString("name"), json.getString("id"));
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching boxes", ex);
        }

        return sort(boxes);
    }

    public static ListBoxModel getBoxes(String cloud, String workspace) {
        return getBoxes(ClientCache.getClient(cloud), workspace);
    }

    public static ListBoxModel getBoxVersions(Client client, String workspace, String box) {
        ListBoxModel boxVersions = new ListBoxModel();
        if (StringUtils.isBlank(box) || client == null) {
            return boxVersions;
        }

        try {
            boxVersions.add("Latest", LATEST_BOX_VERSION);
            for (Object json : client.getBoxVersions(box)) {
                JSONObject boxVersion = (JSONObject) json;
                JSONObject versionObject = boxVersion.getJSONObject("version").getJSONObject("number");
                String displayMessage = MessageFormat.format("Version {0}.{1}.{2} - {3}", versionObject.getInt("major"),
                        versionObject.getInt("minor"), versionObject.getInt("patch"),
                        boxVersion.getJSONObject("version").getString("description"));

                boxVersions.add(displayMessage, boxVersion.getString("id"));
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching box versions", ex);
        }

        return boxVersions;
    }

    public static ListBoxModel getBoxVersions(String cloud, String workspace, String box) {
        return getBoxVersions(ClientCache.getClient(cloud), workspace, box);
    }

    public static String getResolvedBoxVersion(Client client, String workspace, String box, String boxVersion) throws IOException {
        return LATEST_BOX_VERSION.equals(boxVersion) ? client.getLatestBoxVersion(workspace, box) : boxVersion;
    }

    public static ListBoxModel getProfiles(Client client, String workspace, String box) {
        ListBoxModel profiles = new ListBoxModel();
        if (StringUtils.isNotBlank(workspace) && StringUtils.isNotBlank(box) && client != null) {
            try {
                JSONObject boxJson = client.getBox(box);
                if (boxJson.getString("schema").endsWith("/boxes/cloudformation")) {
                    profiles.add(boxJson.getString("name"), box);
                } else {
                    for (Object profile : client.getProfiles(workspace, box)) {
                        JSONObject json = (JSONObject) profile;
                        profiles.add(json.getString("name"), json.getString("id"));
                    }
                    sort(profiles);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Error fetching profiles", ex);
            }
        }
        return profiles;
    }

    public static ListBoxModel getProfiles(String cloud, String workspace, String box) {
        return getProfiles(ClientCache.getClient(cloud), workspace, box);
    }

    public static JSONArrayResponse getBoxStack(Client client, String workspace, String boxId, String boxVersion) {
        if (client != null && StringUtils.isNotBlank(boxId) && StringUtils.isNotBlank(boxVersion)) {
            try {
                if (LATEST_BOX_VERSION.equals(boxVersion)) {
                    boxVersion = client.getLatestBoxVersion(workspace, boxId);
                }
                JSONArray boxStack = new BoxStack(boxVersion, client.getBoxStack(boxVersion), client).toJSONArray();
                for (Object boxJson : boxStack) {
                    for (Object variable : ((JSONObject) boxJson).getJSONArray("variables")) {
                        JSONObject variableJson = (JSONObject) variable;
                        if ("File".equals(variableJson.get("type"))) {
                            String value = variableJson.getString("value");
                            if (StringUtils.isNotBlank(value) && value.startsWith("/services/blobs/download/")) {
                                variableJson.put("value", client.getEndpointUrl() + value);
                            }
                        }
                    }
                }
                return new JSONArrayResponse(boxStack);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for box {0}", boxId), ex);
            }
        }

        return new JSONArrayResponse(new JSONArray());
    }

    public static JSONArrayResponse getBoxStack(String cloud, String workspace, String boxId, String boxVersion) {
        return getBoxStack(ClientCache.getClient(cloud), workspace, boxId, boxVersion);
    }

    public static JSONArrayResponse getInstanceBoxStack(Client client, String instance) {
        if (client != null && StringUtils.isNotBlank(instance)) {
            try {
                JSONObject instanceJson = client.getInstance(instance);
                JSONArray boxes = instanceJson.getJSONArray("boxes");
                List<JSONObject> variables = new ArrayList<JSONObject>();
                if (instanceJson.containsKey("variables")) {
                    for (Object variable : instanceJson.getJSONArray("variables")) {
                        variables.add((JSONObject) variable);
                    }
                }
                BoxStack boxStack = new BoxStack(boxes.getJSONObject(0).getString("id"), boxes, client, variables);
                return new JSONArrayResponse(boxStack.toJSONArray());

            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for profile {0}", instance), ex);
            }
        }

        return new JSONArrayResponse(new JSONArray());
    }

    public static JSONArrayResponse getInstanceBoxStack(String cloud, String instance) {
        return getInstanceBoxStack(ClientCache.getClient(cloud), instance);
    }

    public static class InstanceFilterByTags implements ObjectFilter {
        final Set<String> tags;
        final List<Pattern> tagPatterns;
        final boolean excludeInaccessible;

        public InstanceFilterByTags(Set<String> tags, boolean excludeInaccessible) {
            this.tags = new HashSet<String>();
            Set<String> regExTags = new HashSet<String>();
            for (String tag : tags) {
                if (tag.startsWith("/") && tag.endsWith("/")) {
                    regExTags.add(tag.substring(1, tag.length() - 1));
                } else {
                    this.tags.add(tag);
                }
            }
            tagPatterns = new ArrayList<Pattern>();
            for (String tag : regExTags) {
                tagPatterns.add(Pattern.compile(tag));
            }
            this.excludeInaccessible = excludeInaccessible;
        }

        public boolean accept(JSONObject instance) {
            if (tags.isEmpty() && tagPatterns.isEmpty()) {
                return false;
            }

            Set<String> instanceTags = new HashSet<String>(Arrays.asList((String[])instance.getJSONArray("tags").toArray(new String[0])));
            instanceTags.add(instance.getString("id"));
            boolean hasTags = instanceTags.containsAll(tags);
            if (!hasTags) {
                return false;
            }
            for (Pattern pattern : tagPatterns) {
                boolean matchFound = false;
                for (String tag : instanceTags) {
                    if (pattern.matcher(tag).matches()) {
                        matchFound = true;
                        break;
                    }
                }
                if (!matchFound) {
                    return false;
                }
            }
            if (hasTags && excludeInaccessible) {
                return !Client.InstanceState.UNAVAILABLE.equals(instance.getString("state")) &&
                        !Client.TERMINATE_OPERATIONS.contains(instance.getJSONObject("operation").getString("event"));
            }
            return hasTags;
        }
    }

    public static class InstanceFilterByBox implements ObjectFilter {
        final String boxId;

        public InstanceFilterByBox(String boxId) {
            this.boxId = boxId;
        }

        public boolean accept(JSONObject instance) {
            if (Client.TERMINATE_OPERATIONS.contains(instance.getJSONObject("operation").getString("event"))) {
                return false;
            }

            if (boxId == null || boxId.isEmpty() || boxId.equals(ANY_BOX)) {
                return true;
            }

            return new BoxStack(boxId, instance.getJSONArray("boxes"), null).findBox(boxId) != null;
        }
    }

    public static JSONArray getInstances(Client client, String workspace, ObjectFilter filter) {
        JSONArray instances = new JSONArray();
        if (client == null || StringUtils.isBlank(workspace)) {
            return instances;
        }

        try {
            JSONArray instanceArray = client.getInstances(workspace);
            if (!instanceArray.isEmpty() && !instanceArray.getJSONObject(0).getJSONArray("boxes").getJSONObject(0).containsKey("id")) {
                List<String> instanceIDs = new ArrayList<String>();
                for (int i = 0; i < instanceArray.size(); i++) {
                    instanceIDs.add(instanceArray.getJSONObject(i).getString("id"));
                }
                instanceArray = client.getInstances(workspace, instanceIDs);
            }
            for (Object instance : instanceArray) {
                JSONObject json = (JSONObject) instance;
                if (filter.accept(json)) {
                    instances.add(json);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching instances of workspace {0}", workspace), ex);
        }

        Collections.sort(instances, new Comparator<Object> () {
            public int compare(Object o1, Object o2) {
                return ((JSONObject) o1).getString("name").compareTo(((JSONObject) o2).getString("name"));
            }
        });

        return instances;
    }

    public static JSONArrayResponse getInstancesAsJSONArrayResponse(Client client, String workspace, String box) {
        JSONArray instances = getInstances(client, workspace, new InstanceFilterByBox(box));
        for (Object instance : instances) {
            JSONObject json = (JSONObject) instance;

            json.put("name", MessageFormat.format("{0} - {1}", json.getString("name"), json.getJSONObject("service").getString("id")));
        }

        return new JSONArrayResponse(instances);
    }

    public static JSONArrayResponse getInstancesAsJSONArrayResponse(String cloud, String workspace, String box) {
        return getInstancesAsJSONArrayResponse(ClientCache.getClient(cloud), workspace, box);
    }

    public static ListBoxModel getInstances(Client client, String workspace, String box) {
        ListBoxModel instances = new ListBoxModel();
        JSONArray instanceArray = getInstancesAsJSONArrayResponse(client, workspace, box).getJsonArray();
        for (Object instance : instanceArray) {
            JSONObject json = (JSONObject) instance;
            instances.add(json.getString("name"), json.getString("id"));
        }
        return instances;
    }

    public static ListBoxModel getInstances(String cloud, String workspace, String box) {
        return getInstances(ClientCache.getClient(cloud), workspace, box);
    }

    public static FormValidation checkSlaveBox(Client client, String box) {
        if (client == null || StringUtils.isBlank(box)) {
            return FormValidation.ok();
        }

        JSONArray stack;
        try {
            stack = client.getBoxStack(box);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            return FormValidation.error("Error fetching box stack of box {0}", box);
        }
        if (stack.isEmpty()) {
            return FormValidation.ok();
        }

        String variableListStr = StringUtils.join(SlaveInstance.REQUIRED_VARIABLES, ", ");
        if (SlaveInstance.isSlaveBox(stack.getJSONObject(0))) {
            return FormValidation.ok();
        } else if (stack.size() == 1) {
            return FormValidation.error(MessageFormat.format("The selected box version does not have the following required variables: {0}", variableListStr));
        }

        JSONObject slaveBox = null;
        for (int i = 1; i < stack.size(); i++) {
            JSONObject stackBox = stack.getJSONObject(i);
            if (SlaveInstance.isSlaveBox(stackBox)) {
                slaveBox = stackBox;
                break;
            }
        }

        if (slaveBox != null) {
            return FormValidation.ok(MessageFormat.format("The required variables {0} are detected in child box {1}. They will be set by Jenkins at deployment time.", variableListStr, slaveBox.getString("name")));
        } else {
            String message = MessageFormat.format("The selected box version and its child boxes do not have the following required variables: {0}", variableListStr);
            return FormValidation.error(message);
        }
    }

    public static FormValidation checkCloud(String cloud) {
        if (StringUtils.isBlank(cloud)) {
            return FormValidation.error("Cloud is required");
        }

        try {
            ClientCache.findOrCreateClient(cloud);
            return FormValidation.ok();
        } catch (IOException ex) {
            return FormValidation.error(ex.getMessage() != null ? ex.getMessage() : "Cannot connect to the cloud");
        }
    }

    public static JSONArray removeInvalidVariables(JSONArray variableArray, JSONArray boxStack) {
        Set<String> fullVariableNames = new HashSet<String>();
        for (Object box : boxStack) {
            JSONObject boxJson = (JSONObject) box;
            for (Object var : boxJson.getJSONArray("variables")) {
                JSONObject varJson = (JSONObject) var;
                fullVariableNames.add(varJson.getString("scope") + '.' + varJson.getString("name"));
            }
        }
        for (Iterator iter = variableArray.iterator(); iter.hasNext();) {
            JSONObject varJson = (JSONObject) iter.next();
            String fullVariableName = (varJson.containsKey("scope") ? varJson.getString("scope") : StringUtils.EMPTY) + '.' + varJson.getString("name");
            if (!fullVariableNames.contains(fullVariableName)) {
                iter.remove();
            }

            if (varJson.getString("type").equals("Binding") && StringUtils.isBlank(varJson.getString("value"))) {
                iter.remove();
            }
        }

        return variableArray;
    }

    public static JSONArray removeInvalidVariables(JSONArray variableArray, String instanceId, Client client) {
        if (variableArray == null || variableArray.isEmpty()) {
            return variableArray;
        }
        return removeInvalidVariables(variableArray, getInstanceBoxStack(client, instanceId).getJsonArray());
    }

    public static String fixVariables(String variables, JSONArray boxStack) {
        if (variables == null) {
            return null;
        }

        JSONArray variableArray = VariableResolver.parseVariables(variables);
        removeInvalidVariables(variableArray, boxStack);
        return variableArray.toString();
    }

    public static final String resolveDeploymentPolicy(Client client, String workspaceId, String policy, String commaSeparateClaims) throws IOException {
        String resolvedDeploymentPolicy;
        if (commaSeparateClaims != null) {
            if (StringUtils.isNotBlank(commaSeparateClaims)) {
                Set<String> tags = new HashSet<String>();
                for (String tag : commaSeparateClaims.split(",")) {
                    tags.add(tag.trim());
                }
                List<JSONObject> policies = client.getPolicies(workspaceId, tags);
                if (policies.isEmpty()) {
                    throw new IOException(MessageFormat.format("Cannot find any deployment policy with claims: {0}", commaSeparateClaims));
                } else {
                    resolvedDeploymentPolicy = policies.get(0).getString("id");
                }
            } else if(policy != null && StringUtils.isNotBlank(policy)) {
                resolvedDeploymentPolicy = policy;
            } else {
                throw new IOException(MessageFormat.format("Claims are required to select a deployment policy", commaSeparateClaims));
            }
        } else {
            resolvedDeploymentPolicy = policy;
        }
        return resolvedDeploymentPolicy;
    }

    public static JSONArray getInstances(Set<String> tags, String cloud, String workspace, boolean excludeInaccessible) {
        return getInstances(ClientCache.getClient(cloud), workspace, new InstanceFilterByTags(tags, excludeInaccessible));
    }

    public static JSONArray getInstances(Set<String> tags, String cloud, String workspace, String boxVersion) {
        return getInstances(ClientCache.getClient(cloud), workspace,
                new CompositeObjectFilter(new InstanceFilterByTags(tags, false), new InstanceFilterByBox((boxVersion))));
    }

    public static void fixDeploymentPolicyFormData(JSONObject formData) {
        if (formData.getString("cloudFormationSelected").equals("true")) {
            formData.remove("profile");
            formData.remove("claims");
        } else {
            String policySelection = null;
            for (Object entry : formData.entrySet()) {
                Map.Entry mapEntry = (Map.Entry) entry;
                if (mapEntry.getKey().toString().startsWith("policySelection-")) {
                    policySelection = mapEntry.getValue().toString();
                    break;
                }
            }
            if ("claims".equals(policySelection)) {
                formData.remove("profile");
            } else {
                formData.remove("claims");
            }
            formData.remove("provider");
            formData.remove("location");
        }
    }

    private static ListBoxModel sort(ListBoxModel model) {
        Collections.sort(model, new Comparator<ListBoxModel.Option> () {
            public int compare(ListBoxModel.Option o1, ListBoxModel.Option o2) {
                return o1.name.compareTo(o2.name);
            }
        });
        return model;
    }

}
