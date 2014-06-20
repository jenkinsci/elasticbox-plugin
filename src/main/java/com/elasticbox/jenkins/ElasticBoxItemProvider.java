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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import javax.servlet.ServletException;
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
    
    public static class VariableArray implements HttpResponse {
        private final JSONArray jsonArray;
        
        public VariableArray(JSONArray jsonArray) {
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
    
    public ListBoxModel getWorkspaces() {
        ListBoxModel workspaces = new ListBoxModel();
        try {
            Client ebClient = createClient();
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
        if (workspace.trim().length() == 0) {
            return boxes;
        }

        try {
            Client ebClient = createClient();
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

    public ListBoxModel getProfiles(String workspace, String box) {
        ListBoxModel profiles = new ListBoxModel();
        if (box.trim().length() == 0) {
            return profiles;
        }

        try {
            Client ebClient = createClient();
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
    
    public VariableArray getProfileVariables(String profile) {
        if (profile != null && profile.trim().length() > 0) {
            try {
                return new VariableArray(createClient().getProfileVariables(profile));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for profile {0}", profile), ex);
            }
        }
        
        return new VariableArray(new JSONArray());
    }
    
    /**
     * Returns only the variables of main box for now.
     * 
     * @param instance
     * @return 
     */
    public VariableArray getInstanceVariables(String instance) {
        if (instance != null && instance.trim().length() > 0) {
            try {
                JSONObject json = createClient().getInstance(instance);
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
                return new VariableArray(variables);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for instance {0}", instance), ex);
            }
        }
        
        return new VariableArray(new JSONArray());        
    }
    
    private static class InstanceFilter {
        private final String filter;

        InstanceFilter(String filter) {
            this.filter = filter != null ? filter.toLowerCase() : filter;
        }
        
        public boolean accept(JSONObject instance) {
            if (filter == null || filter.isEmpty() || 
                    instance.getString("name").toLowerCase().contains(filter) || 
                    instance.getString("environment").toLowerCase().contains(filter)) {
                return true;
            };
            
            Object tags = instance.get("tags");
            if (tags instanceof String[]) {
                for (String tag : (String[]) tags) {
                    if (tag.toLowerCase().contains(filter)) {
                        return true;
                    }
                }
            }
            
            return false;
        }
    }
    
    public ListBoxModel getInstances(String workspace, String filter) {
        ListBoxModel instances = new ListBoxModel();
        try {
            JSONArray instanceArray = createClient().getInstances(workspace);
            InstanceFilter instanceFilter = new InstanceFilter(filter);
            for (Object instance : instanceArray) {
                JSONObject json = (JSONObject) instance;
                if (instanceFilter.accept(json)) {
                    instances.add(MessageFormat.format("{0} - {1}", json.getString("name"), json.getString("environment")), json.getString("id"));
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching instances of workspace {0}", workspace), ex);
        }
        return sort(instances);
    }
    
    private ListBoxModel sort(ListBoxModel model) {
        Collections.sort(model, new Comparator<ListBoxModel.Option> () {
            public int compare(ListBoxModel.Option o1, ListBoxModel.Option o2) {
                return o1.name.compareTo(o2.name);
            }
        });
        return model;
    }

    private Client createClient() throws IOException {
        ElasticBoxCloud ebCloud = ElasticBoxCloud.getInstance();
        if (ebCloud != null) {
            return ebCloud.createClient();
        }
        return null;
    }
        
}
