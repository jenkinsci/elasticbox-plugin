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

import com.elasticbox.Client;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import javax.servlet.ServletException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxItemProvider {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxItemProvider.class.getName());
    private final Client client;
    
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
    
    public ElasticBoxItemProvider() {
        this(null);
    }
    
    public ElasticBoxItemProvider(Client client) {
        this.client = client;
    }
    
    public ListBoxModel getWorkspaces() {
        ListBoxModel workspaces = new ListBoxModel();
        try {
            Client ebClient = getClient();
            if (ebClient != null) {
                for (Object workspace : ebClient.getWorkspaces()) {
                    JSONObject json = (JSONObject) workspace;
                    workspaces.add(json.getString("name"), json.getString("id"));
                }                    
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching workspaces", ex);
        }

        return sort(workspaces);        
    }

    public ListBoxModel getBoxes(String workspace) {
        ListBoxModel boxes = new ListBoxModel();
        if (StringUtils.isBlank(workspace)) {
            return boxes;
        }

        try {
            Client ebClient = getClient();
            if (ebClient != null) {
                for (Object box : ebClient.getBoxes(workspace)) {
                    JSONObject json = (JSONObject) box;
                    boxes.add(json.getString("name"), json.getString("id"));
                }                    
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching boxes", ex);
        }                

        return sort(boxes);        
    }
    
    public ListBoxModel getBoxVersions(String box) {
        ListBoxModel boxVersions = new ListBoxModel();
        if (StringUtils.isBlank(box)) {
            return boxVersions;
        }
        
        try {
            Client client = getClient();
            if (client != null) {
                for (Object json : client.getBoxVersions(box)) {
                    JSONObject boxVersion = (JSONObject) json;
                    boxVersions.add(boxVersion.getJSONObject("version").getString("description"), boxVersion.getString("id"));
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching box versions", ex);
        }
        
        return boxVersions;
    }

    public ListBoxModel getProfiles(String workspace, String box) {
        ListBoxModel profiles = new ListBoxModel();
        if (StringUtils.isBlank(workspace) || StringUtils.isBlank(box)) {
            return profiles;
        }

        try {
            Client ebClient = getClient();
            if (ebClient != null) {
                for (Object profile : ebClient.getProfiles(workspace, box)) {
                    JSONObject json = (JSONObject) profile;
                    profiles.add(json.getString("name"), json.getString("id"));
                }                    
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching profiles", ex);
        }

        return sort(profiles);        
    }
    
    public JSONArrayResponse getBoxStack(String boxId) {
        if (StringUtils.isNotBlank(boxId)) {
            try {
                Client client = getClient();
                return new JSONArrayResponse(new BoxStack(boxId, client.getBoxStack(boxId), client).toJSONArray());
                
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for box {0}", boxId), ex);
            }
        }
        
        return new JSONArrayResponse(new JSONArray());        
    }
    
    public JSONArrayResponse getProfileBoxStack(String profile) {
        if (StringUtils.isNotBlank(profile)) {
            try {
                Client client = getClient();
                JSONObject profileJson = client.getProfile(profile);
                String boxId = profileJson.getJSONObject("box").getString("version");
                return new JSONArrayResponse(new BoxStack(boxId, client.getBoxStack(boxId), client).toJSONArray());
                
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for profile {0}", profile), ex);
            }
        }
        
        return new JSONArrayResponse(new JSONArray());
    }

    public JSONArrayResponse getInstanceBoxStack(String instance) {
        if (!StringUtils.isBlank(instance)) {
            try {
                Client client = getClient();
                JSONObject instanceJson = client.getInstance(instance);
                JSONArray boxes = instanceJson.getJSONArray("boxes");
                return new JSONArrayResponse(new BoxStack(boxes.getJSONObject(0).getString("id"), boxes, client).toJSONArray());
                
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for profile {0}", instance), ex);
            }
        }
        
        return new JSONArrayResponse(new JSONArray());
    }

    /**
     * Returns only the variables of main box for now.
     * 
     * @param instance
     * @return 
     */
    public JSONArrayResponse getInstanceVariables(String instance) {
        if (StringUtils.isBlank(instance)) {
            try {
                JSONObject json = getClient().getInstance(instance);
                JSONArray variables = json.getJSONArray("boxes").getJSONObject(0).getJSONArray("variables");
                for (Object modifiedVariable : json.getJSONArray("variables")) {
                    JSONObject modifiedVariableJson = (JSONObject) modifiedVariable;
                    for (Object variable : variables) {
                        JSONObject variableJson = (JSONObject) variable;
                        if (variableJson.getString("name").equals(modifiedVariableJson.getString("name"))) {
                            variableJson.put("value", modifiedVariableJson.getString("value"));
                            break;
                        }
                    }
                 }
                return new JSONArrayResponse(variables);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for instance {0}", instance), ex);
            }
        }
        
        return new JSONArrayResponse(new JSONArray());        
    }
    
    private static class InstanceFilter {
        private final String boxId;

        InstanceFilter(String boxId) {
            this.boxId = boxId;
        }
        
        public boolean accept(JSONObject instance) {
            if (Client.TERMINATE_OPERATIONS.contains(instance.getString("operation"))) {
                return false;
            }
            
            if (boxId == null || boxId.isEmpty() || boxId.equals("AnyBox")) {
                return true;
            }
            
            return new BoxStack(boxId, instance.getJSONArray("boxes"), null).findBox(boxId) != null;
        }
    }
    
    public JSONArrayResponse getInstancesAsJSONArrayResponse(String workspace, String box) {
        JSONArray instances = new JSONArray();
        try {
            Client client = getClient();
            JSONArray instanceArray = client.getInstances(workspace);
            if (!instanceArray.isEmpty() && !instanceArray.getJSONObject(0).getJSONArray("boxes").getJSONObject(0).containsKey("id")) {
                List<String> instanceIDs = new ArrayList<String>();
                for (int i = 0; i < instanceArray.size(); i++) {
                    instanceIDs.add(instanceArray.getJSONObject(i).getString("id"));
                }
                instanceArray = client.getInstances(workspace, instanceIDs);
            }
            InstanceFilter instanceFilter = new InstanceFilter(box);
            for (Object instance : instanceArray) {
                JSONObject json = (JSONObject) instance;
                if (instanceFilter.accept(json)) {
                    json.put("name", MessageFormat.format("{0} - {1} - {2}", json.getString("name"), 
                            json.getString("environment"), json.getJSONObject("service").getString("id")));
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
        
        return new JSONArrayResponse(instances);
    }
    
    public ListBoxModel getInstances(String workspace, String box) {
        ListBoxModel instances = new ListBoxModel();
        if (!workspace.isEmpty() && !box.isEmpty()) {
            JSONArray instanceArray = getInstancesAsJSONArrayResponse(workspace, box).getJsonArray();
            for (Object instance : instanceArray) {
                JSONObject json = (JSONObject) instance;
                instances.add(json.getString("name"), json.getString("id"));
            }
        }
        return instances;
    }
    
    private ListBoxModel sort(ListBoxModel model) {
        Collections.sort(model, new Comparator<ListBoxModel.Option> () {
            public int compare(ListBoxModel.Option o1, ListBoxModel.Option o2) {
                return o1.name.compareTo(o2.name);
            }
        });
        return model;
    }

    private Client getClient() throws IOException {
        if (client != null) {
            return client;
        }
        
        ElasticBoxCloud ebCloud = ElasticBoxCloud.getInstance();
        if (ebCloud != null) {
            return ebCloud.createClient();
        }
        
        return null;
    }
    
    private static class BoxStack {
        private final List<JSONObject> overriddenVariables;
        private final JSONArray boxes;
        private final String boxId;
        private final Client client;
        
        BoxStack(String boxId, JSONArray boxes, Client client) {
            this.boxId = boxId;
            this.boxes = boxes;      
            this.client = client;
            overriddenVariables = new ArrayList<JSONObject>();
        }
        
        public JSONArray toJSONArray() {
            return JSONArray.fromObject(createBoxStack("", boxId));
        }
        
        private JSONObject findBox(String boxId) {
            for (Object json : boxes) {
                JSONObject box = (JSONObject) json;
                if (box.getString("id").equals(boxId)) {
                    return box;
                }
            }
            
            for (Object json : boxes) {
                JSONObject box = (JSONObject) json;
                if (box.containsKey("version") && box.getJSONObject("version").getString("box").equals(boxId)) {
                    return box;
                }
            }      
            
            return null;
        }
        
        private JSONObject findOverriddenVariable(String name, String scope) {
            for (Object json : overriddenVariables) {
                JSONObject variable = (JSONObject) json;
                if (scope.equals(variable.get("scope")) && variable.get("name").equals(name)) {
                    return variable;
                }
            }

            return null;
        }

        private List<JSONObject> createBoxStack(String scope, String boxId) {
            JSONObject box = findBox(boxId);
            if (box == null) {
                return Collections.EMPTY_LIST;
            }
            
            List<JSONObject> boxStack = new ArrayList<JSONObject>();
            JSONObject stackBox = new JSONObject();
            String icon = null;
            if (box.containsKey("icon")) {
                icon = box.getString("icon");
            }
            if (icon == null || icon.isEmpty()) {
                icon = "/images/platform/box.png";
            } else if (icon.charAt(0) != '/') {
                icon = '/' + icon;
            }
            stackBox.put("id", box.getString("id"));
            stackBox.put("name", box.getString("name"));
            stackBox.put("icon", client.getEndpointUrl() + icon);
            boxStack.add(stackBox);
            JSONArray stackBoxVariables = new JSONArray();
            JSONArray variables = box.getJSONArray("variables");
            List<JSONObject> boxVariables = new ArrayList<JSONObject>();
            for (Object json : variables) {
                JSONObject variable = (JSONObject) json;
                String varScope = (String) variable.get("scope");
                if (varScope != null && !varScope.isEmpty()) {
                    String fullScope = scope.isEmpty() ? varScope : scope + '.' + varScope;
                    if (findOverriddenVariable(variable.getString("name"), scope) == null) {
                        JSONObject overriddenVariable = JSONObject.fromObject(variable);
                        overriddenVariable.put("scope", fullScope);
                        overriddenVariables.add(variable);
                    }
                } else if (variable.getString("type").equals("Box")) {
                    boxVariables.add(variable);
                } else {
                    JSONObject stackBoxVariable = JSONObject.fromObject(variable);
                    stackBoxVariable.put("scope", scope);
                    JSONObject overriddenVariable = findOverriddenVariable(stackBoxVariable.getString("name"), scope);
                    if (overriddenVariable != null) {
                        stackBoxVariable.put("value", overriddenVariable.get("value"));
                    }
                    stackBoxVariables.add(stackBoxVariable);
                }
            }        
            stackBox.put("variables", stackBoxVariables);

            for (JSONObject boxVariable : boxVariables) {
                String variableName = boxVariable.getString("name");
                boxStack.addAll(createBoxStack(scope.isEmpty() ? variableName : scope + '.' + variableName, boxVariable.getString("value")));
            }

            return boxStack;
        }
            
    }
        
}
