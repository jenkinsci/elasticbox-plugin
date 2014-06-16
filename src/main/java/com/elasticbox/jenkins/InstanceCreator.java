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
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class InstanceCreator extends BuildWrapper {
    private static final Logger LOGGER = Logger.getLogger(InstanceCreator.class.getName());

    private static Client getClient() throws IOException {
        ElasticBoxCloud ebCloud = ElasticBoxCloud.getInstance();
        if (ebCloud != null) {
            Client client = new Client(ebCloud.getEndpointUrl(), ebCloud.getUsername(), ebCloud.getPassword());
            client.connect();
            return client;
        }
        return null;
    }
    
    private final String workspace;
    private final String box;
    private final String profile;
    private transient ElasticBoxSlave ebSlave;

    
    @DataBoundConstructor
    public InstanceCreator(String workspace, String box, String profile) {
        super();
        this.workspace = workspace;
        this.box = box;
        this.profile = profile;
    }
    
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {        
        for (Node node : build.getProject().getAssignedLabel().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getComputer().getBuilds().contains(build)) {
                    ebSlave = slave;
                    ebSlave.setInUse(true);
                    break;
                }
            }
        }

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                ebSlave.setInUse(false);
                ebSlave.setCloud(null);
                if (ebSlave.isSingleUse()) {
                    ebSlave.getComputer().setAcceptingTasks(false);
                }                        
                build.getProject().setAssignedLabel(null);
                return true;
            }
        };
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }

    public String getProfile() {
        return profile;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox Instance Creation";
        }
        
        private ListBoxModel sort(ListBoxModel model) {
            Collections.sort(model, new Comparator<ListBoxModel.Option> () {
                public int compare(ListBoxModel.Option o1, ListBoxModel.Option o2) {
                    return o1.name.compareTo(o2.name);
                }
            });
            return model;
        }

        public ListBoxModel doFillWorkspaceItems() {
            ListBoxModel workspaces = new ListBoxModel();
            try {
                Client ebClient = InstanceCreator.getClient();
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
        
        public ListBoxModel doFillBoxItems(@QueryParameter String workspace) {
            ListBoxModel boxes = new ListBoxModel();
            if (workspace.trim().length() == 0) {
                return boxes;
            }

            try {
                Client ebClient = InstanceCreator.getClient();
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

        public ListBoxModel doFillProfileItems(@QueryParameter String workspace, @QueryParameter String box) {                
            ListBoxModel profiles = new ListBoxModel();
            if (box.trim().length() == 0) {
                return profiles;
            }

            try {
                Client ebClient = InstanceCreator.getClient();
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
    }
}
