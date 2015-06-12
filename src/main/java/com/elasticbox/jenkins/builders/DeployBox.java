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
import com.elasticbox.Constants;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.ClientCache;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private String boxVersion;
    private final String profile;
    private final String claims;
    private final String provider;
    private final String location;
    private final String instanceName;
    @Deprecated
    private final String environment;
    @Deprecated
    private final int instances;
    private final String variables;
    private final InstanceExpiration expiration;
    private final String autoUpdates;
    private final String instanceEnvVariable;
    private String tags;
    @Deprecated
    private boolean skipIfExisting;
    private String alternateAction;
    private boolean waitForCompletion;
    private int waitForCompletionTimeout;

    private transient InstanceManager instanceManager;

    @DataBoundConstructor
    public DeployBox(String id, String cloud, String workspace, String box, String boxVersion, String instanceName, String profile,
            String claims, String provider, String location, String instanceEnvVariable, String tags,
            String variables, InstanceExpiration expiration, String autoUpdates, String alternateAction,
            boolean waitForCompletion, int waitForCompletionTimeout) {
        super();
        assert id != null && id.startsWith(getClass().getName() + '-');
        this.id = id;
        this.cloud = cloud;
        this.workspace = workspace;
        this.box = box;
        this.boxVersion = boxVersion;
        this.profile = profile;
        this.claims = claims;
        this.provider = provider;
        this.location = location;
        this.instances = 0;
        this.environment = null;
        this.variables = variables;
        this.expiration = expiration;
        this.autoUpdates = autoUpdates;
        this.alternateAction = alternateAction;
        this.waitForCompletion = waitForCompletion;
        this.waitForCompletionTimeout = waitForCompletionTimeout;
        this.tags = tags;
        this.instanceEnvVariable = instanceEnvVariable;
        this.instanceName = instanceName;

        readResolve();
    }

    private Result performAlternateAction(JSONArray existingInstances, ElasticBoxCloud ebCloud, Client client,
            VariableResolver resolver, TaskLogger logger, AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        JSONObject instance = existingInstances.getJSONObject(0);
        boolean existing = true;
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
            String instanceId = deploy(ebCloud, client, resolver, logger, build);
            instance = client.getInstance(instanceId);
            existing = false;
        } else {
            throw new IOException(MessageFormat.format("Invalid alternate action: ''{0}''", alternateAction));
        }

        return new Result(instance, existing);
    }

    private String deploy(ElasticBoxCloud ebCloud, Client client, VariableResolver resolver, TaskLogger logger,
            AbstractBuild<?, ?> build)
            throws IOException, InterruptedException {
        Set<String> resolvedTags = resolver.resolveTags(tags);
        JSONArray resolvedVariables = resolver.resolveVariables(variables);
        DescriptorHelper.removeInvalidVariables(resolvedVariables,
                ((DescriptorImpl) getDescriptor()).doGetBoxStack(cloud, workspace, box, boxVersion).getJsonArray());
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
        String boxId = DescriptorHelper.getResolvedBoxVersion(client, workspace, box, boxVersion);
        String policyId = DescriptorHelper.resolveDeploymentPolicy(client, workspace, profile, claims);
        logger.info("Deploying box {0} with policy {1}", client.getBoxPageUrl(boxId), client.getBoxPageUrl(policyId));
        JSONArray policyVariables = new JSONArray();
        if (StringUtils.isNotBlank(provider)) {
            JSONObject providerVariable = new JSONObject();
            providerVariable.put("type", "Text");
            providerVariable.put("name", "provider_id");
            providerVariable.put("value", provider);
            policyVariables.add(providerVariable);
            JSONObject locationVariable = new JSONObject();
            locationVariable.put("type", "Text");
            locationVariable.put("name", "location");
            locationVariable.put("value", location);
        }
        IProgressMonitor monitor = client.deploy(boxId, policyId, resolver.resolve(instanceName), workspace,
                new ArrayList(resolvedTags), resolvedVariables, expirationTime, expirationOperation,
                policyVariables, autoUpdates);
        String instanceId = Client.getResourceId(monitor.getResourceUrl());
        String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), client.getInstance(instanceId));
        logger.info("Instance {0} is being deployed", instancePageUrl);
        notifyDeploying(build, instanceId, ebCloud);
        if (waitForCompletion) {
            try {
                logger.info("Waiting for the deployment of the instance {0} to finish", instancePageUrl);
                monitor.waitForDone(getWaitForCompletionTimeout());
                logger.info("The instance {0} has been deployed successfully ", instancePageUrl);
            } catch (IProgressMonitor.IncompleteException ex) {
                Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                logger.error("Failed to deploy instance {0}: {1}", instancePageUrl, ex.getMessage());
                throw new AbortException(ex.getMessage());
            }
        }

        return Client.getResourceId(monitor.getResourceUrl());
    }

    private void injectEnvVariables(AbstractBuild build, final Result result, final Client client) throws IOException {
        final String instanceId = result.instance.getString("id");
        final JSONObject service = client.getService(instanceId);
        build.addAction(new EnvironmentContributingAction() {

            public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
                final String instanceUrl = client.getInstanceUrl(instanceId);
                env.put(instanceEnvVariable, instanceId);
                env.put(instanceEnvVariable + "_URL", instanceUrl);
                env.put(instanceEnvVariable + "_SERVICE_ID", service.getString("id"));
                env.put(instanceEnvVariable + "_TAGS", StringUtils.join(result.instance.getJSONArray("tags"), ","));
                env.put(instanceEnvVariable + "_IS_EXISTING", String.valueOf(result.existing));

                int instances = 1;
                if (service.containsKey("profile") && service.getJSONObject("profile").containsKey("instances")) {
                    instances = service.getJSONObject("profile").getInt("instances");
                }

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

    private static class Result {
        JSONObject instance;
        boolean existing;

        public Result(JSONObject instance, boolean existing) {
            this.instance = instance;
            this.existing = existing;
        }
    }

    private Result doPerform(AbstractBuild<?, ?> build, ElasticBoxCloud ebCloud, TaskLogger logger) throws InterruptedException, IOException {
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
                return performAlternateAction(existingInstances, ebCloud, client, resolver, logger, build);
            }
        }

        final String instanceId = deploy(ebCloud, client, resolver, logger, build);
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
        return new Result(instance, false);
    }

    private void notifyDeploying(AbstractBuild<?, ?> build, String instanceId, ElasticBoxCloud ebxCloud) throws InterruptedException {
        for (BuilderListener listener: Jenkins.getInstance().getExtensionList(BuilderListener.class)) {
            try {
                listener.onDeploying(build, instanceId, ebxCloud);
            } catch (IOException ex) {
                Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Deploy Box build step");

        ElasticBoxCloud ebCloud = (ElasticBoxCloud) Jenkins.getInstance().getCloud(getCloud());
        if (ebCloud == null) {
            throw new IOException(MessageFormat.format("Cannod find ElasticBox cloud ''{0}''.", getCloud()));
        }


        Result result = doPerform(build, ebCloud, logger);
        instanceManager.setInstance(build, result.instance);

        if (StringUtils.isNotBlank(instanceEnvVariable)) {
            injectEnvVariables(build, result, ebCloud.getClient());
        }

        return true;

    }

    public String getId() {
        return id;
    }

    protected final Object readResolve() {
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

        if (boxVersion != null && boxVersion.equals(box)) {
            boxVersion = DescriptorHelper.LATEST_BOX_VERSION;
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

    public String getClaims() {
        return claims;
    }

    public String getProvider() {
        return provider;
    }

    public String getLocation() {
        return location;
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

    public String getAutoUpdates() {
        return autoUpdates;
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

    public String getInstanceName() {
        return instanceName;
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
        private static final Pattern ENV_VARIABLE_PATTERN = Pattern.compile("^[a-zA-Z_]+[a-zA-Z0-9_]*$");
        private static String boxName;

        private static final ListBoxModel alternateActionItems = new ListBoxModel();
        private static final ListBoxModel autoUpdatesItems = new ListBoxModel();
        static {
            alternateActionItems.add("still perform deployment", ACTION_NONE);
            alternateActionItems.add("skip deployment", ACTION_SKIP);
            alternateActionItems.add("reconfigure", ACTION_RECONFIGURE);
            alternateActionItems.add("reinstall", ACTION_REINSTALL);
            alternateActionItems.add("delete and deploy again", ACTION_DELETE_AND_DEPLOY);

            autoUpdatesItems.add("Off", Constants.AUTOMATIC_UPDATES_OFF);
            autoUpdatesItems.add("All Updates", Constants.AUTOMATIC_UPDATES_MAJOR);
            autoUpdatesItems.add("Minor & Patch Updates", Constants.AUTOMATIC_UPDATES_MINOR);
            autoUpdatesItems.add("Patch Updates", Constants.AUTOMATIC_UPDATES_PATCH);
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
            DescriptorHelper.fixDeploymentPolicyFormData(formData);

            String instanceEnvVariable = formData.getString("instanceEnvVariable").trim();
            if (!instanceEnvVariable.isEmpty() && !ENV_VARIABLE_PATTERN.matcher(instanceEnvVariable).find()) {
                throw new FormException("Environment variable for the instance can have only alphanumeric characters and begins with a letter or underscore", "instanceEnvVariable");
            }

            if (formData.containsKey("variables")) {
                JSONArray boxStack = doGetBoxStack(formData.getString("cloud"), formData.getString("workspace"),
                        formData.getString("box"), formData.getString("boxVersion")).getJsonArray();
                formData.put("variables", DescriptorHelper.fixVariables(formData.getString("variables"), boxStack));
            }

            fixExpirationFormData(formData);
            DeployBox deployBox = (DeployBox) super.newInstance(req, formData);

            if (deployBox.claims != null) {
                if (StringUtils.isBlank(deployBox.claims)) {
                    throw new FormException("Claims are required to look for a policy to deploy a box in ElasticBox", "claims");
                }
            } else if (deployBox.profile != null) {
                if (StringUtils.isBlank(deployBox.profile)) {
                    throw new FormException("No policy is selected to deploy box in ElasticBox", "profile");
                }
            } else if (deployBox.provider != null) {
                if (StringUtils.isBlank(deployBox.provider)) {
                    throw new FormException("Provider is required to deploy a CloudFormation box in ElasticBox", "provider");
                }
                if (StringUtils.isBlank(deployBox.location)) {
                    throw new FormException("Region is required to deploy a CloudFormation box in ElasticBox", "location");
                }
            } else {
                throw new FormException(MessageFormat.format("No deployment option is selected for ''{0}'' build step", getDisplayName()), null);
            }


            if (deployBox.getExpiration() instanceof InstanceExpirationSchedule) {
                InstanceExpirationSchedule expirationSchedule = (InstanceExpirationSchedule) deployBox.getExpiration();
                if (expirationSchedule.getHours() == null) {
                    FormValidation validation = InstanceExpirationSchedule.checkDate(expirationSchedule.getDate());
                    if (validation.kind == FormValidation.Kind.ERROR) {
                        throw new FormException(MessageFormat.format("Invalid date specified for Expiration of ''{0}'' build step: {1}", getDisplayName(), validation.getMessage()), "expiration");
                    }
                    validation = InstanceExpirationSchedule.checkTime(expirationSchedule.getTime());
                    if (validation.kind == FormValidation.Kind.ERROR) {
                        throw new FormException(MessageFormat.format("Invalid time specified for Expiration of ''{0}'' build step: {0}", getDisplayName(), validation.getMessage()), "expiration");
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

        public ListBoxModel doFillBoxVersionItems(@QueryParameter String cloud, @QueryParameter String workspace,
                @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(cloud, workspace, box);
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String cloud, @QueryParameter String workspace,
                @QueryParameter String box) {
            return DescriptorHelper.getProfiles(cloud, workspace, box);
        }

        public ListBoxModel doFillProviderItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            return DescriptorHelper.getCloudFormationProviders(ClientCache.getClient(cloud), workspace);
        }

        public ListBoxModel doFillLocationItems(@QueryParameter String cloud, @QueryParameter String provider) {
            return DescriptorHelper.getCloudFormationLocations(ClientCache.getClient(cloud), provider);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(@QueryParameter String cloud,
                @QueryParameter String workspace, @QueryParameter String box, @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(cloud, workspace, box, StringUtils.isBlank(boxVersion) ? box : boxVersion);
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

        public ListBoxModel doFillAutoUpdatesItems() {
            return autoUpdatesItems;
        }

        public String getBoxName(String cloud, String workspace, String boxId) {
            String boxName = "";
            ListBoxModel boxes = DescriptorHelper.getBoxes(cloud, workspace);
            for (ListBoxModel.Option option : boxes) {
                if (option.value.equals(boxId)) {
                    boxName = option.name;
                }
            }

            return boxName;
        }

        public String doFillInstanceName(@QueryParameter String cloud, @QueryParameter String workspace, @QueryParameter String boxId) {
            String boxName = "";
            ListBoxModel boxes = DescriptorHelper.getBoxes(cloud, workspace);
            for (ListBoxModel.Option option : boxes) {
                if (option.value.equals(boxId)) {
                    boxName = option.name;
                }
            }

            return boxName;
        }

        public String uniqueId() {
            return UUID.randomUUID().toString();
        }

        private void fixExpirationFormData(JSONObject formData) {
            JSONObject expiration = formData.getJSONObject("expiration");
            String scheduleType = null;
            for (Object entry : expiration.entrySet()) {
                Map.Entry mapEntry = (Map.Entry) entry;
                if (mapEntry.getKey().toString().startsWith("scheduleType-")) {
                    scheduleType = mapEntry.getValue().toString();
                    break;
                }
            }
            if (scheduleType != null) {
                if ("date-time".equals(scheduleType)) {
                    expiration.remove("hours");
                } else {
                    expiration.remove("date");
                    expiration.remove("time");
                }
            }
        }
    }
}
