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

package com.elasticbox.jenkins.builders;

import com.elasticbox.Client;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.ElasticBoxItemProvider;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class LaunchBox extends Builder {
    private final String workspace;
    private final String box;
    private final String profile;
    private final String environment;
    private final int instances;
    private final String variables;

    @DataBoundConstructor
    public LaunchBox(String workspace, String box, String profile, int instances, String environment, String variables) {
        super();
        this.workspace = workspace;
        this.box = box;
        this.profile = profile;
        this.instances = instances;
        this.environment = environment;
        this.variables = variables;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ElasticBoxCloud cloud = ElasticBoxCloud.getInstance();
        if (cloud == null) {
            throw new IOException("No ElasticBox cloud is configured.");
        }
        Map<String, String> vars = new HashMap<String, String>();
        for (Object variable : JSONArray.fromObject(variables)) {
            JSONObject json = (JSONObject) variable;
            vars.put(json.getString("name"), json.getString("value"));
        }
        IProgressMonitor monitor = cloud.createClient().deploy(profile, workspace, environment, instances, vars);
        String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), monitor.getResourceUrl());
        listener.getLogger().println(MessageFormat.format("Deploying box instance {0}", instancePageUrl));
        listener.getLogger().println(MessageFormat.format("Waiting for the deployment of the box instance {0} to finish", instancePageUrl));
        try {
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            listener.getLogger().println(MessageFormat.format("The box instance {0} has been deployed successfully ", instancePageUrl));
            return true;
        } catch (IProgressMonitor.IncompleteException ex) {
            Logger.getLogger(LaunchBox.class.getName()).log(Level.SEVERE, null, ex);
            listener.error("Failed to deploy box instance %s: %s", instancePageUrl, ex.getMessage());
            throw new AbortException(ex.getMessage());
        }
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

    public String getEnvironment() {
        return environment;
    }        

    public String getVariables() {
        return variables;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private final ElasticBoxItemProvider itemProvider = new ElasticBoxItemProvider();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox - Launch Box";
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.getString("profile").trim().length() == 0) {
                throw new FormException("Profile is required to launch a box in ElasticBox", "profile");
            }
            
            try {
                int instances = formData.getInt("instances");
                if (instances < 1) {
                    throw new FormException("Number of instances must be a positive number to launch a box in ElasticBox", "instances");
                }
            } catch (JSONException ex) {
                throw new FormException(ex.getMessage(), "instances");
            }
            
            if (formData.getString("environment").trim().length() == 0) {
                throw new FormException("Enviroment is required to launch a box in ElasticBox", "environment");
            }                        
            
            return super.newInstance(req, formData);
        }                

        public ListBoxModel doFillWorkspaceItems() {
            return itemProvider.getWorkspaces();
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String workspace) {
            return itemProvider.getBoxes(workspace);
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String workspace, @QueryParameter String box) {                
            return itemProvider.getProfiles(workspace, box);
        }
        
        public ElasticBoxItemProvider.VariableArray doGetVariables(@QueryParameter String profile) {
            return itemProvider.getProfileVariables(profile);
        }
                
    }
}
