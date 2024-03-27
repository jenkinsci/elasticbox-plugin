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

import com.elasticbox.jenkins.migration.RetentionTimeConverter;
import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.Constants;
import com.elasticbox.jenkins.util.JsonUtil;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Items;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;

import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ElasticBoxSlave extends Slave {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxSlave.class.getName());

    private static final String SINGLE_USE_TYPE = "Single-use";
    private static final String PER_PROJECT_TYPE = "Per project configured";
    private static final String GLOBAL_TYPE = "Glocally configured";

    private static final int ID_PREFIX_LENGTH = 21;
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    public static final int MAX_DELETE_ATTEMPTS = 10;

    private final String boxVersion;
    private String profileId;
    private final boolean singleUse;
    private String instanceUrl;
    private String instanceStatusMessage;
    private int retentionTime;
    private int builds;
    private final String cloudName;
    private short deleteAttempts;
    private boolean removableFromCloud = true;

    private final transient int launchTimeout;

    private static String randomId(Random random) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
        }
        return sb.toString();
    }

    private static synchronized String generateName(ElasticBoxCloud cloud, String boxVersion) throws IOException {
        JSONObject boxJson = cloud.getClient().getBox(boxVersion);
        String prefix = boxJson.getString("name").replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase();
        if (prefix.length() > ID_PREFIX_LENGTH) {
            prefix = prefix.substring(0, ID_PREFIX_LENGTH);
        } else if (prefix.length() < ID_PREFIX_LENGTH) {
            StringBuilder padding = new StringBuilder();
            for (int i = prefix.length(); i < ID_PREFIX_LENGTH; i++) {
                padding.append('-');
            }
            prefix += padding.toString();
        }

        Random random = new Random();
        String name;
        do {
            name = prefix + '-' + randomId(random);
        } while (Jenkins.get().getNode(name) != null);

        return name;
    }

    public ElasticBoxSlave(ProjectSlaveConfiguration config, boolean singleUse)
            throws Descriptor.FormException, IOException {

        this(config, config.getElasticBoxCloud(), new ProjectSlaveConfigurationRetentionStrategy(config), singleUse);
    }

    public ElasticBoxSlave(SlaveConfiguration config, ElasticBoxCloud cloud)
            throws Descriptor.FormException, IOException {

        this(config, cloud, new SlaveConfigurationRetentionStrategy(config, cloud), false);
    }

    public ElasticBoxSlave(AbstractSlaveConfiguration config, ElasticBoxCloud cloud,
            RetentionStrategy retentionStrategy, boolean singleUse) throws Descriptor.FormException, IOException {

        super(generateName(cloud, config.resolveBoxVersion(cloud.getClient() )),
                StringUtils.isBlank(config.getRemoteFs() )
                        ? getRemoteFs(config.resolveDeploymentPolicy(cloud.getClient()), cloud)
                        : config.getRemoteFs(),
                new JNLPLauncher(true)

        );

        setNodeDescription(config.getDescription());
        setNumExecutors(config.getExecutors());
        setMode(config.getMode());
        setLabelString(config.getLabels());
        setRetentionStrategy(retentionStrategy);
        setNodeProperties(Collections.EMPTY_LIST);

        this.boxVersion = config.getResolvedBoxVersion();
        this.profileId = config.getResolvedDeploymentPolicy();
        this.singleUse = singleUse;
        this.cloudName = cloud.name;
        this.retentionTime = config.getRetentionTime();
        this.launchTimeout = config.getLaunchTimeout();
    }

    @Override
    protected Object readResolve() {
        RetentionStrategy retentionStrategy = getRetentionStrategy();
        if (retentionStrategy instanceof SlaveConfigurationRetentionStrategy) {
            ElasticBoxCloud cloud = null;
            try {
                cloud = getCloud();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
            if (cloud != null) {
                ((SlaveConfigurationRetentionStrategy) retentionStrategy).cloudName = cloud.name;
            }
        } else if (!(retentionStrategy instanceof ElasticBoxRetentionStrategy)) {
            setRetentionStrategy(new IdleTimeoutRetentionStrategy(retentionTime));
        }

        return super.readResolve();
    }


    @Override
    public Computer createComputer() {
        return new ElasticBoxComputer(this);
    }

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public String getInstancePageUrl() throws IOException {
        checkInstanceReachable();
        return Client.getPageUrl(getCloud().getEndpointUrl(), instanceUrl);
    }

    public String getInstanceId() {
        return Client.getResourceId(instanceUrl);
    }

    public boolean isSingleUse() {
        return singleUse;
    }

    public void setDeletable(boolean deletable) {
        if (deletable) {
            this.deleteAttempts++;
            if (maxDeleteAttemptsReached() ) {
                LOGGER.warning(MessageFormat.format(
                        "MAX_DELETE_ATTEMPTS reached. Attempted to delete slave [{0}] {1} times. Giving up.",
                        this.toString(), MAX_DELETE_ATTEMPTS));

                this.setRemovableFromCloud(false);
            }
        } else {
            this.deleteAttempts = 0;
        }
    }

    public boolean isDeletable() {
        return deleteAttempts > 0;
    }

    public boolean maxDeleteAttemptsReached() {
        return deleteAttempts > MAX_DELETE_ATTEMPTS;
    }

    public ElasticBoxCloud getCloud() throws IOException {
        ElasticBoxCloud ebCloud = null;
        if (cloudName != null) {
            Cloud cloud = Jenkins.get().getCloud(cloudName);
            if (cloud instanceof ElasticBoxCloud) {
                ebCloud = (ElasticBoxCloud) cloud;
            } else {
                throw new IOException(
                        MessageFormat.format("Cannot find any ElasticBox cloud with name ''{0}''", cloudName));
            }
        }

        return ebCloud != null ? ebCloud : ElasticBoxCloud.getInstance();
    }

    public AbstractSlaveConfiguration getSlaveConfiguration() {
        if (getRetentionStrategy() instanceof AbstractSlaveConfigurationRetentionStrategy) {
            return ((AbstractSlaveConfigurationRetentionStrategy) getRetentionStrategy()).getSlaveConfiguration();
        }

        return null;
    }

    public JSONArray getPolicyVariables() {
        AbstractSlaveConfiguration slaveConfig = getSlaveConfiguration();
        if (slaveConfig != null) {
            return JsonUtil.createCloudFormationDeployVariables(slaveConfig.getProvider(), slaveConfig.getLocation());
        }

        return null;
    }

    public void setInstanceStatusMessage(String message) {
        this.instanceStatusMessage = message;
    }

    public String getInstanceStatusMessage() {
        return instanceStatusMessage;
    }

    public String getType() {
        if (isSingleUse()) {
            return SINGLE_USE_TYPE;
        } else if (StringUtils.isBlank(getLabelString())) {
            return PER_PROJECT_TYPE;
        } else {
            return GLOBAL_TYPE;
        }
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public int getLaunchTimeout() {
        return launchTimeout;
    }

    public int getRetentionTime() {
        return ((ElasticBoxRetentionStrategy) getRetentionStrategy()).getRetentionTime();
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public void terminate() throws IOException {
        checkInstanceReachable();
        Client client = getCloud().getClient();
        String instanceId = getInstanceId();
        try {
            LOGGER.info("Terminating slave - " + toString());
            client.terminate(instanceId);

        } catch (ClientException ex) {

            if (ex.getStatusCode() == HttpStatus.SC_CONFLICT) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Forcing slave termination - " + toString());
                }
                client.forceTerminate(instanceId);
            }
        }
        ElasticBoxSlaveHandler.addToTerminatedQueue(this);
    }

    public void delete() throws IOException {
        checkInstanceReachable();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Deleting slave - " + toString());
        }
        getCloud().getClient().delete(getInstanceId());
    }

    public boolean isTerminated() throws IOException {
        JSONObject instance = getInstance();
        String state = instance.getString("state");
        String operation = instance.getJSONObject("operation").getString("event");

        return Client.InstanceState.DONE.equals(state)
                && Client.TERMINATE_OPERATIONS.contains(operation);
    }

    public JSONObject getInstance() throws IOException {
        checkInstanceReachable();
        return getCloud().getClient().getInstance(getInstanceId());
    }

    public JSONObject getProfile() throws IOException {
        checkInstanceReachable();
        return getCloud().getClient().getBox(getProfileId());
    }

    void checkInstanceReachable() throws IOException {
        ElasticBoxCloud ebCloud = getCloud();
        if (ebCloud == null) {
            throw new IOException("No ElasticBox cloud is found");
        }
        if (instanceUrl == null) {
            throw new IOException("Slave doesn't have a deployed instance");
        }
        if (!instanceUrl.startsWith(ebCloud.getEndpointUrl())) {
            throw new IOException(
                    MessageFormat.format(
                            "The instance {0} has been created at a different ElasticBox endpoint than the currently"
                                    + " configured one. Open {0} in a browser to terminate it.",
                            instanceUrl));
        }
    }

    public String getInstanceState() throws IOException {
        JSONObject instance = getInstance();
        return instance.getString("state");
    }

    void markForTermination() {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Marking slave for termination - " + toString() );
        }
        setDeletable(true);
        SlaveComputer computer = getComputer();
        if (computer != null) {
            computer.setAcceptingTasks(false);
            computer.setTemporarilyOffline(true, new OfflineCause() {

                @Override
                public String toString() {
                    String message;
                    ElasticBoxCloud cloud = null;
                    String instanceUrl = getInstanceUrl();
                    try {
                        cloud = getCloud();
                    } catch (IOException ex) {
                        LOGGER.log(
                                Level.SEVERE,
                                MessageFormat.format("Error trying to get the cloud for instance {0}", instanceUrl));
                    }
                    if (instanceUrl == null || cloud == null) {
                        message = "This slave will be removed shortly";
                    } else {
                        String url = Client.getPageUrl(((ElasticBoxCloud) cloud).getEndpointUrl(), instanceUrl);
                        if (url != null) {
                            message = MessageFormat.format(
                                    "Instance at {0} of ElasticBox cloud ''{1}'' will be terminated and deleted",
                                    url,
                                    cloud.getDisplayName());
                        } else {
                            message = MessageFormat.format(
                                    "Instance {0} must be terminated but that's not possible because the endpoint URL"
                                            + " of ElasticBox cloud ''{1}'' has been changed",
                                    instanceUrl,
                                    cloud.getDisplayName());
                        }
                    }
                    return message;
                }

            });
        } else {
            save();
        }
    }

    void incrementBuilds() {
        builds++;
        save();
    }

    boolean hasExpired() {
        if (isSingleUse() && builds > 0) {
            return true;
        }

        AbstractSlaveConfiguration slaveConfig = getSlaveConfiguration();
        return slaveConfig != null && (slaveConfig.getRetentionTime() == 0
                || (slaveConfig.getMaxBuilds() > 0 && builds >= slaveConfig.getMaxBuilds()));
    }

    public void save() {
        try {
            Jenkins.get().save();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    private static String getRemoteFs(String profileId, ElasticBoxCloud cloud) throws IOException {
        Client client = cloud.getClient();
        JSONObject profile = client.getBox(profileId);
        JSONArray claims = profile.getJSONArray("claims");
        if (claims.contains(Constants.LINUX_CLAIM)) {
            return "/var/jenkins";
        } else if (claims.contains(Constants.WINDOWS_CLAIM)) {
            return "C:\\Jenkins";
        } else {
            throw new IOException(
                    MessageFormat.format(
                            "Cannot create slave since the selected deployment policy ''{0}'' supports"
                                    + " neither Linux nor Windows.",
                            profile.getString("name")));
        }
    }

    public boolean isRemovableFromCloud() {
        return removableFromCloud;
    }

    public void setRemovableFromCloud(boolean removableFromCloud) {
        this.removableFromCloud = removableFromCloud;
    }

    private abstract static class ElasticBoxRetentionStrategy extends RetentionStrategy<ElasticBoxComputer> {

        public abstract boolean shouldTerminate(ElasticBoxComputer computer);

        protected abstract int getRetentionTime();

        @Override
        public boolean isManualLaunchAllowed(ElasticBoxComputer computer) {
            return false;
        }

        @Override
        public synchronized long check(ElasticBoxComputer computer) {
            if (shouldTerminate(computer)) {
                LOGGER.info(
                        MessageFormat.format(
                                "Retention time of {0} minutes is elapsed for slave {1}. The computer is terminating",
                                getRetentionTime(),
                                computer.getSlave().getDisplayName()));

                computer.terminate();
            }

            return 1;
        }

    }

    private static class IdleTimeoutRetentionStrategy extends ElasticBoxRetentionStrategy {
        private int retentionTime;

        @DataBoundConstructor
        public IdleTimeoutRetentionStrategy(int retentionTime) {
            this.retentionTime = retentionTime;
        }

        @Override
        public boolean shouldTerminate(ElasticBoxComputer computer) {
            return computer.getIdleTime() > TimeUnit.MINUTES.toMillis(getRetentionTime());
        }

        @Override
        protected int getRetentionTime() {
            return retentionTime;
        }

        public static class ConverterImpl extends RetentionTimeConverter<IdleTimeoutRetentionStrategy> {

            @Override
            protected void fixZeroRetentionTime(IdleTimeoutRetentionStrategy obj) {
                if (obj.getRetentionTime() == 0) {
                    obj.retentionTime = Integer.MAX_VALUE;
                }
            }

        }

    }

    private static final class SlaveConfigurationRetentionStrategy extends AbstractSlaveConfigurationRetentionStrategy {
        private transient String cloudName;

        public SlaveConfigurationRetentionStrategy(AbstractSlaveConfiguration slaveConfig, ElasticBoxCloud cloud) {
            super(slaveConfig);
            this.cloudName = cloud.name;
        }

        @Override
        protected SlaveConfiguration getSlaveConfiguration() {
            Cloud cloud = Jenkins.get().getCloud(cloudName);
            if (cloud instanceof ElasticBoxCloud) {
                return ((ElasticBoxCloud) cloud).getSlaveConfiguration(slaveConfigId);
            }

            return null;
        }
    }

    private static final class ProjectSlaveConfigurationRetentionStrategy
            extends AbstractSlaveConfigurationRetentionStrategy {

        public ProjectSlaveConfigurationRetentionStrategy(ProjectSlaveConfiguration slaveConfig) {
            super(slaveConfig);
        }

        @Override
        protected ProjectSlaveConfiguration getSlaveConfiguration() {
            return ProjectSlaveConfiguration.find(slaveConfigId);
        }

    }

    private abstract static class AbstractSlaveConfigurationRetentionStrategy extends IdleTimeoutRetentionStrategy {

        protected final String slaveConfigId;
        protected final int minInstances;

        AbstractSlaveConfigurationRetentionStrategy(AbstractSlaveConfiguration slaveConfig) {
            super(slaveConfig.getRetentionTime());
            this.slaveConfigId = slaveConfig.getId();
            this.minInstances = slaveConfig.getMinInstances();
        }

        protected abstract AbstractSlaveConfiguration getSlaveConfiguration();

        @Override
        protected int getRetentionTime() {
            if (getSlaveConfiguration() != null) {
                return getSlaveConfiguration().getRetentionTime();
            }

            return super.getRetentionTime();
        }

        private int getMinInstances() {
            if (getSlaveConfiguration() != null) {
                return getSlaveConfiguration().getMinInstances();
            }

            return minInstances;
        }

        @Override
        public boolean shouldTerminate(ElasticBoxComputer computer) {
            if (getMinInstances() > 0 && getSlaveConfiguration() != null) {
                List<JSONObject> activeInstances;
                try {
                    activeInstances = ElasticBoxSlaveHandler.getActiveInstances(computer.getSlave().getCloud());
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    // cannot decide whether the slave should be terminated because the active instances
                    // could not be fetched
                    // leave it alone for now
                    return false;
                }
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("Checking Slave - " + computer.getSlave() );
                }

                if (activeInstances.size() <= getMinInstances()) {
                    return false;
                }

                Set<String> configActiveInstanceIDs = new HashSet<String>();
                for (Node node : Jenkins.get().getNodes()) {
                    if (node instanceof ElasticBoxSlave) {
                        ElasticBoxSlave slave = (ElasticBoxSlave) node;
                        if (slave.getSlaveConfiguration() == getSlaveConfiguration()) {
                            configActiveInstanceIDs.add(slave.getInstanceId());
                        }
                    }
                }

                if (configActiveInstanceIDs.isEmpty()) {
                    return false;
                }

                int instanceCount = 0;
                for (JSONObject instance : activeInstances) {
                    if (configActiveInstanceIDs.contains(instance.getString("id"))) {
                        instanceCount++;
                    }
                }

                if (configActiveInstanceIDs.contains(computer.getSlave().getInstanceId())
                        && instanceCount <= getMinInstances()) {

                    return false;
                }
            }

            return super.shouldTerminate(computer);
        }

    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox Slave";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }

        @Override
        public Node newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            throw new FormException("This slave cannot be updated.", "");
        }

        @Initializer(before = InitMilestone.PLUGINS_STARTED)
        public static void addAliases() {
            Items.XSTREAM2.addCompatibilityAlias("com.elasticbox.jenkins.ElasticBoxSlave.RetentionStrategyImpl",
                    IdleTimeoutRetentionStrategy.class);
        }
    }

    public static class ConverterImpl extends RetentionTimeConverter<ElasticBoxSlave> {

        @Override
        protected void fixZeroRetentionTime(ElasticBoxSlave slave) {
            if (slave.getRetentionTime() == 0) {
                slave.retentionTime = Integer.MAX_VALUE;
            }
            if (slave.getRetentionStrategy() instanceof IdleTimeoutRetentionStrategy) {

                IdleTimeoutRetentionStrategy retentionStrategy =
                        (IdleTimeoutRetentionStrategy) slave.getRetentionStrategy();

                if (retentionStrategy.getRetentionTime() == 0) {
                    retentionStrategy.retentionTime = Integer.MAX_VALUE;
                }
            }
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(100).append("Name:").append(getNodeName());

        if (instanceUrl != null) {
            sb.append(". Url:").append(instanceUrl);
            try {
                String operation = getInstance().getJSONObject("operation").getString("event");
                sb.append(". LastOp:").append(operation);
                String str = getInstance().getString("state");
                sb.append(". Status:").append(str);
            } catch (IOException e) {
                sb.append(". Message:").append(e.getMessage());
            }
        } else {
            String statusMessage = getInstanceStatusMessage();
            if (statusMessage != null) {
                sb.append(". StatusMessage:").append(statusMessage);
            }
        }
        return sb.toString();
    }
}
