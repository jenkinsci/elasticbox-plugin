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
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class DeployBox extends Builder implements IInstanceProvider {    
    private static final String ACTION_NONE = "none";
    private static final String ACTION_SKIP = "skip";
    private static final String ACTION_REINSTALL = Client.InstanceOperation.REINSTALL;
    private static final String ACTION_DELETE_AND_DEPLOY = "deleteAndDeploy";
    
    private final String id;
    private String cloud;
    private final String workspace;
    private final String box;
    private final String boxVersion;
    private final String profile;
    private final String environment;
    private final int instances;
    private final String variables;
    
    @Deprecated
    private boolean skipIfExisting;
    private String alternateAction;
    
    private transient InstanceManager instanceManager;
    private boolean waitForCompletion;

    @DataBoundConstructor
    public DeployBox(String id, String cloud, String workspace, String box, String boxVersion, String profile, 
            int instances, String environment, String variables, String alternateAction, boolean waitForCompletion) {
        super();
        assert id != null && id.startsWith(getClass().getName() + '-');
        this.id = id;
        this.cloud = cloud;
        this.workspace = workspace;
        this.box = box;
        this.boxVersion = boxVersion;
        this.profile = profile;
        this.instances = instances;
        this.environment = environment;
        this.variables = variables;
        this.alternateAction = alternateAction;
        this.waitForCompletion = waitForCompletion;
        
        readResolve();
    }
    
    private JSONObject performAlternateAction(JSONObject existingInstance, ElasticBoxCloud ebCloud, Client client, 
            VariableResolver resolver, TaskLogger logger) throws IOException {
        JSONObject instance = existingInstance;
        if (alternateAction.equals(ACTION_SKIP)) {
            String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), 
                    ebCloud.getEndpointUrl() + existingInstance.getString("uri"));
            logger.info("Existing instance found: {0}. Deployment skipped.", instancePageUrl);        
        } else if (alternateAction.equals(ACTION_REINSTALL)) {
            ReinstallBox.reinstall(existingInstance.getString("id"), ebCloud, client, waitForCompletion, logger);
        } else if (alternateAction.equals(ACTION_DELETE_AND_DEPLOY)) {
            String instanceId = existingInstance.getString("id");
            TerminateBox.terminate(instanceId, ebCloud, client, logger);
            client.delete(instanceId);
            instanceId = deploy(ebCloud, client, resolver, logger);
            instance = client.getInstance(instanceId);
        } else {
            throw new IOException(MessageFormat.format("Invalid alternate action: ''{0}''", alternateAction));
        }
        
        return instance;
    }
    
    private String deploy(ElasticBoxCloud ebCloud, Client client, VariableResolver resolver, TaskLogger logger) throws IOException {
        String resolvedEnvironment = resolver.resolve(this.environment);
        JSONArray jsonVariables = JSONArray.fromObject(variables);
        for (Object variable : jsonVariables) {
            resolver.resolve((JSONObject) variable);
        }        
        IProgressMonitor monitor = client.deploy(profile, workspace, resolvedEnvironment, instances, jsonVariables);
        String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl());
        logger.info("Deploying box instance {0}", instancePageUrl);
        if (waitForCompletion) {
            try {
                logger.info("Waiting for the deployment of the box instance {0} to finish", instancePageUrl);
                monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
                logger.info("The box instance {0} has been deployed successfully ", instancePageUrl);
            } catch (IProgressMonitor.IncompleteException ex) {
                Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                logger.error("Failed to deploy box instance {0}: {1}", instancePageUrl, ex.getMessage());
                throw new AbortException(ex.getMessage());
            }      
        }
        
        return Client.getResourceId(monitor.getResourceUrl());
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {       
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Deploy Box build step");
        
        ElasticBoxCloud ebCloud = (ElasticBoxCloud) Jenkins.getInstance().getCloud(getCloud());
        if (ebCloud == null) {
            throw new IOException("No ElasticBox cloud is configured.");
        }

        VariableResolver resolver = new VariableResolver(build, listener);
        String resolvedEnvironment = resolver.resolve(this.environment);
        
        Client client = ebCloud.createClient();
        if (!alternateAction.equals(ACTION_NONE)) {
            JSONArray instanceArray = DescriptorHelper.getInstancesAsJSONArrayResponse(client, workspace, box).getJsonArray();
            JSONObject existingInstance = null;
            for (Object instance : instanceArray) {
                JSONObject json = (JSONObject) instance;
                if (json.getString("environment").equals(resolvedEnvironment)) {
                    existingInstance = json;
                    break;
                }
            }
            if (existingInstance != null) {
                JSONObject instance = performAlternateAction(existingInstance, ebCloud, client, resolver, logger);
                instanceManager.setInstance(build, instance);
                return true;            
            }
        }
        
        String instanceId = deploy(ebCloud, client, resolver, logger);
        instanceManager.setInstance(build, client.getInstance(instanceId));
        return true;
        
    }        

    public String getId() {
        return id;
    }

    protected Object readResolve() {
        if (alternateAction == null) {
            alternateAction = skipIfExisting ? ACTION_SKIP : ACTION_NONE;
            waitForCompletion = true;
        }
            
        if (cloud == null) {
            ElasticBoxCloud ebCloud = ElasticBoxCloud.getInstance();
            if (ebCloud != null) {
                cloud = ebCloud.name;
            }
        }
        
        if (instanceManager == null) {
            instanceManager = new InstanceManager();
        }
        
        return this;
    }

    public String getCloud() {
        return cloud;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public String getProfile() {
        return profile;
    }  

    public int getInstances() {
        return instances;
    }
    
    public String getEnvironment() {
        return environment;
    }        

    public String getVariables() {
        return variables;
    }

    public String getAlternateAction() {
        return alternateAction;
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    public String getInstanceId(AbstractBuild build) {
        JSONObject instance = instanceManager.getInstance(build);
        return instance != null ? instance.getString("id") : null;
    }

    public ElasticBoxCloud getElasticBoxCloud() {
        return (ElasticBoxCloud) Jenkins.getInstance().getCloud(cloud);
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private static final ListBoxModel alternateActionItems = new ListBoxModel();
        static {
            alternateActionItems.add("still perform deployment", ACTION_NONE);
            alternateActionItems.add("skip deployment", ACTION_SKIP);
            alternateActionItems.add("reinstall", ACTION_REINSTALL);
            alternateActionItems.add("delete and deploy again", ACTION_DELETE_AND_DEPLOY);            
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox - Deploy Box";
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

        public ListBoxModel doFillCloudItems() {
            return DescriptorHelper.getClouds();
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {
            return DescriptorHelper.getWorkspaces(cloud);
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(cloud, workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@QueryParameter String cloud, @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(cloud, box);
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String cloud, @QueryParameter String workspace, 
                @QueryParameter String box) {                
            return DescriptorHelper.getProfiles(cloud, workspace, box);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(@QueryParameter String cloud, 
                @QueryParameter String box, @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(cloud, StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public DescriptorHelper.JSONArrayResponse doGetInstances(@QueryParameter String cloud, 
                @QueryParameter String workspace, @QueryParameter String box, @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJSONArrayResponse(cloud, workspace, 
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }
        
        public FormValidation doCheckCloud(@QueryParameter String value) {
            return DescriptorHelper.checkCloud(value);
        }
        
        public ListBoxModel doFillAlternateActionItems() {
            return alternateActionItems;
        }
        
    }
}
