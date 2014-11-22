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
import com.elasticbox.ClientException;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.CompositeObjectFilter;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributingAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
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
    private static final String ACTION_RECONFIGURE = Client.InstanceOperation.RECONFIGURE;
    private static final String ACTION_REINSTALL = Client.InstanceOperation.REINSTALL;
    private static final String ACTION_DELETE_AND_DEPLOY = "deleteAndDeploy";
    
    private final String id;
    private String cloud;
    private final String workspace;
    private final String box;
    private final String boxVersion;
    private final String profile;
    @Deprecated
    private final String environment;
    private final int instances;
    private final String variables;
    private final InstanceExpiration expiration;
    private final String instanceEnvVariable;
    private String tags;    
    @Deprecated
    private boolean skipIfExisting;
    private String alternateAction;
    private boolean waitForCompletion;
    private int waitForCompletionTimeout;

    private transient InstanceManager instanceManager;

    @DataBoundConstructor
    public DeployBox(String id, String cloud, String workspace, String box, String boxVersion, String profile, 
            int instances, String instanceEnvVariable, String tags, String variables, InstanceExpiration expiration,
            String alternateAction, boolean waitForCompletion, int waitForCompletionTimeout) {
        super();
        assert id != null && id.startsWith(getClass().getName() + '-');
        this.id = id;
        this.cloud = cloud;
        this.workspace = workspace;
        this.box = box;
        this.boxVersion = boxVersion;
        this.profile = profile;
        this.instances = instances;
        this.environment = null;
        this.variables = variables;
        this.expiration = expiration;
        this.alternateAction = alternateAction;
        this.waitForCompletion = waitForCompletion;
        this.waitForCompletionTimeout = waitForCompletionTimeout;
        this.tags = tags;
        this.instanceEnvVariable = instanceEnvVariable;
        
        readResolve();
    }
    
    private JSONObject performAlternateAction(JSONArray existingInstances, ElasticBoxCloud ebCloud, Client client, 
            VariableResolver resolver, TaskLogger logger) throws IOException, InterruptedException {
        JSONObject instance = existingInstances.getJSONObject(0);
        if (alternateAction.equals(ACTION_SKIP)) {
            String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), instance);
            logger.info("Existing instance found: {0}. Deployment skipped.", instancePageUrl);
        } else if (alternateAction.equals(ACTION_RECONFIGURE)) {            
            ReconfigureOperation.reconfigure(existingInstances, resolver.resolveVariables(variables),
                    waitForCompletionTimeout, client, logger);
        } else if (alternateAction.equals(ACTION_REINSTALL)) {
            ReinstallOperation.reinstall(existingInstances, resolver.resolveVariables(variables),
                    waitForCompletionTimeout, client, logger);
            
        } else if (alternateAction.equals(ACTION_DELETE_AND_DEPLOY)) {
            for (Object existingInstance : existingInstances) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }            
                JSONObject instanceJson = (JSONObject) existingInstance;
                // Don't fail if the instance is not found
                try {
                    TerminateOperation.terminate(instanceJson, client, logger);
                    client.delete(instanceJson.getString("id"));
                } catch (ClientException ex) {
                    if (ex.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                        throw  ex;
                    }
                }
            }
            String instanceId = deploy(ebCloud, client, resolver, logger);
            instance = client.getInstance(instanceId);
        } else {
            throw new IOException(MessageFormat.format("Invalid alternate action: ''{0}''", alternateAction));
        }
        
        return instance;
    }
    
    private String deploy(ElasticBoxCloud ebCloud, Client client, VariableResolver resolver, TaskLogger logger) 
            throws IOException, InterruptedException {
        String resolvedEnvironment = resolver.resolve(tags.split(",")[0].trim());
        JSONArray resolvedVariables = resolver.resolveVariables(variables);
        DescriptorHelper.removeInvalidVariables(resolvedVariables, 
                ((DescriptorImpl) getDescriptor()).doGetBoxStack(cloud, box, boxVersion).getJsonArray());
        String expirationTime = null, expirationOperation = null;
        if (getExpiration() instanceof InstanceExpirationSchedule) {
            InstanceExpirationSchedule expirationSchedule = (InstanceExpirationSchedule) getExpiration();
            try {
                expirationTime = expirationSchedule.getUTCDateTime();
            } catch (ParseException ex) {
                Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                logger.error("Error parsing expiration time: {0}", ex.getMessage());
                throw new AbortException(ex.getMessage());
            }
            expirationOperation = expirationSchedule.getOperation();
        }
        IProgressMonitor monitor = client.deploy(boxVersion, profile, workspace, resolvedEnvironment, instances, 
                resolvedVariables, expirationTime, expirationOperation);
        String instanceId = Client.getResourceId(monitor.getResourceUrl());
        String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), client.getInstance(instanceId));
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
    
    private void injectEnvVariables(AbstractBuild build, final JSONObject instance, final Client client) throws IOException {
        final String instanceId = instance.getString("id");
        final JSONObject service = client.getService(instanceId);
        build.addAction(new EnvironmentContributingAction() {

            public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
                final String instanceUrl = client.getInstanceUrl(instanceId);            
                env.put(instanceEnvVariable, instanceId);
                env.put(instanceEnvVariable + "_URL", instanceUrl);
                env.put(instanceEnvVariable + "_SERVICE_ID", service.getString("id"));
                env.put(instanceEnvVariable + "_TAGS", StringUtils.join(instance.getJSONArray("tags"), ","));                
                if (instances == 1) {
                    JSONObject address = null;
                    if (service.containsKey("address")) {
                        address = service.getJSONObject("address");
                    } else if (service.containsKey("machines")) {
                        JSONArray machines = service.getJSONArray("machines");
                        if (!machines.isEmpty()) {
                            JSONObject machine = machines.getJSONObject(0);
                            env.put(instanceEnvVariable + "_MACHINE_NAME", machine.getString("name"));
                            if (machine.containsKey("address")) {
                                address = machine.getJSONObject("address");
                            }
                        }
                    }
                    if (address != null) {
                        env.put(instanceEnvVariable + "_PUBLIC_ADDRESS", address.getString("public"));
                        env.put(instanceEnvVariable + "_PRIVATE_ADDRESS", address.getString("private"));                   
                    }
                } else if (instances > 1 && service.containsKey("machines")) {
                    List<String> machineNames = new ArrayList<String>();
                    List<String> publicAddresses = new ArrayList<String>();
                    List<String> privateAddresses = new ArrayList<String>();
                    for (Object machine : service.getJSONArray("machines")) {
                        JSONObject machineJson = (JSONObject) machine;     
                        machineNames.add(machineJson.getString("name"));
                        if (machineJson.containsKey("address")) {
                            JSONObject address = machineJson.getJSONObject("address");
                            publicAddresses.add(address.getString("public"));
                            privateAddresses.add(address.getString("private"));
                        }
                    }
                    env.put(instanceEnvVariable + "_MACHINE_NAMES", StringUtils.join(machineNames, " "));
                    env.put(instanceEnvVariable + "_PUBLIC_ADDRESSES", StringUtils.join(publicAddresses, " "));
                    env.put(instanceEnvVariable + "_PRIVATE_ADDRESSES", StringUtils.join(privateAddresses, " "));                   
                }
            }

            public String getIconFileName() {
                return null;
            }

            public String getDisplayName() {
                return "Instance Environment Variables";
            }

            public String getUrlName() {
                return null;
            }

        });        
    }
    
    private JSONObject doPerform(AbstractBuild<?, ?> build, ElasticBoxCloud ebCloud, TaskLogger logger) throws InterruptedException, IOException {
        VariableResolver resolver = new VariableResolver(cloud, workspace, build, logger.getTaskListener());
        Client client = ebCloud.getClient();
        if (!alternateAction.equals(ACTION_NONE)) {
            Set<String> tagSet = resolver.resolveTags(tags);
            CompositeObjectFilter instanceFilter = new CompositeObjectFilter(new DescriptorHelper.InstanceFilterByBox(box));
            if (alternateAction.equals(ACTION_RECONFIGURE)) {
                instanceFilter.add(ReconfigureOperation.instanceFilter(tagSet));
            } else if (alternateAction.equals(ACTION_REINSTALL)) {
                instanceFilter.add(ReinstallOperation.instanceFilter(tagSet));
            } else if (alternateAction.equals(ACTION_DELETE_AND_DEPLOY)) {
                instanceFilter.add(new DescriptorHelper.InstanceFilterByTags(tagSet, false));
            } else {
                instanceFilter.add(new DescriptorHelper.InstanceFilterByTags(tagSet, true));
            }        
            JSONArray existingInstances = DescriptorHelper.getInstances(client, workspace, instanceFilter);
            if (!existingInstances.isEmpty()) {
                return performAlternateAction(existingInstances, ebCloud, client, resolver, logger);
            }
        }
        
        final String instanceId = deploy(ebCloud, client, resolver, logger);
        JSONObject instance = client.getInstance(instanceId);
        Set<String> resolvedTags = resolver.resolveTags(tags);
        if (waitForCompletion && !resolvedTags.isEmpty()) {
            JSONArray instanceTags = instance.getJSONArray("tags");
            int oldSize = instanceTags.size();
            for (String tag : resolvedTags) {
                if (!instanceTags.contains(tag)) {
                    instanceTags.add(tag);
                }
            }
            if (instanceTags.size() > oldSize) {
                instance.put("tags", instanceTags);
                instance = client.updateInstance(instance);
            }
        }
        return instance;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {       
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Deploy Box build step");

        ElasticBoxCloud ebCloud = (ElasticBoxCloud) Jenkins.getInstance().getCloud(getCloud());
        if (ebCloud == null) {
            throw new IOException(MessageFormat.format("Cannod find ElasticBox cloud ''{0}''.", getCloud()));
        }
        
        
        JSONObject instance = doPerform(build, ebCloud, logger);
        instanceManager.setInstance(build, instance);
        
        if (StringUtils.isNotBlank(instanceEnvVariable)) {
            injectEnvVariables(build, instance, ebCloud.getClient());
        }
        
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
        
        if (StringUtils.isNotBlank(environment)) {
            tags = StringUtils.isBlank(tags) ? environment : (environment + ',' + tags);
        }

        if (waitForCompletion && waitForCompletionTimeout == 0) {
            waitForCompletionTimeout = ElasticBoxSlaveHandler.TIMEOUT_MINUTES;
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
    
    public String getInstanceEnvVariable() {
        return instanceEnvVariable;
    }        

    public String getTags() {
        return tags;
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

    public InstanceExpiration getExpiration() {
        return expiration;
    }

    public int getWaitForCompletionTimeout() {
        return waitForCompletionTimeout;
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
        private static final Pattern ENVIRONMENT_PATTERN = Pattern.compile("[a-zA-Z0-9-]+");
        private static final Pattern ENV_VARIABLE_PATTERN = Pattern.compile("^[a-zA-Z_]+[a-zA-Z0-9_]*$");
        
        private static final InstanceExpiration alwaysOn = new InstanceExpiration() {
            
        };

        private static final ListBoxModel alternateActionItems = new ListBoxModel();
        static {
            alternateActionItems.add("still perform deployment", ACTION_NONE);
            alternateActionItems.add("skip deployment", ACTION_SKIP);
            alternateActionItems.add("reconfigure", ACTION_RECONFIGURE);
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

        public List<? extends Descriptor<InstanceExpiration>> getExpirationOptions() {
            return Jenkins.getInstance().getDescriptorList(InstanceExpiration.class);
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
            
            String tags = formData.getString("tags").trim();
            if (tags.length() == 0) {                
                throw new FormException("Tags are required to launch a box in ElasticBox", "tags");
            }     
            String environment = tags.split(",")[0].trim();
            if (!ENVIRONMENT_PATTERN.matcher(environment).find()) {
                throw new FormException("The first tag can contains only alpha-numerical character or dash (-)", "tags");
            }            
            
            String instanceEnvVariable = formData.getString("instanceEnvVariable").trim();
            if (!instanceEnvVariable.isEmpty() && !ENV_VARIABLE_PATTERN.matcher(instanceEnvVariable).find()) {
                throw new FormException("Environment variable for the instance can have only alphanumeric characters and begins with a letter or underscore", "instanceEnvVariable");
            }
            
            if (formData.containsKey("variables")) {
                JSONArray boxStack = doGetBoxStack(formData.getString("cloud"), formData.getString("box"), formData.getString("boxVersion")).getJsonArray();
                formData.put("variables", DescriptorHelper.fixVariables(formData.getString("variables"), boxStack));
            }

            JSONObject expiration = formData.getJSONObject("expiration");
            if (expiration.containsKey("scheduleType")) {
                if ("date-time".equals(expiration.get("scheduleType"))) {
                    expiration.remove("hours");
                } else {
                    expiration.remove("date");
                    expiration.remove("time");
                }        
            }
            
            DeployBox deployBox = (DeployBox) super.newInstance(req, formData);
            
            if (deployBox.getExpiration() instanceof InstanceExpirationSchedule) {
                InstanceExpirationSchedule expirationSchedule = (InstanceExpirationSchedule) deployBox.getExpiration();
                if (expirationSchedule.getHours() == null) {
                    FormValidation validation = InstanceExpirationSchedule.checkDate(expirationSchedule.getDate());
                    if (validation.kind == FormValidation.Kind.ERROR) {
                        throw new FormException(MessageFormat.format("Invalid date specified for Expiration of DeployBox build step: {0}", validation.getMessage()), "expiration");
                    }
                    validation = InstanceExpirationSchedule.checkTime(expirationSchedule.getTime());
                    if (validation.kind == FormValidation.Kind.ERROR) {
                        throw new FormException(MessageFormat.format("Invalid time specified for Expiration of DeployBox build step: {0}", validation.getMessage()), "expiration");
                    }                    
                }
            }
            
            return deployBox;
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
