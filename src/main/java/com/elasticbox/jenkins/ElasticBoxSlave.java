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
import com.elasticbox.ClientException;
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
import hudson.slaves.RetentionStrategy;
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
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;


/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxSlave extends Slave {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxSlave.class.getName());
    
    private static final String SINGLE_USE_TYPE = "Single-use";
    private static final String PER_PROJECT_TYPE = "Per project configured";
    private static final String GLOBAL_TYPE = "Glocally configured";
    
    private static final int ID_PREFIX_LENGTH = 21;
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    
    private static String randomId(Random random) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
        }
        return sb.toString();
    }
    
    private static synchronized String generateName(ElasticBoxCloud cloud, String boxVersion) throws IOException {
        JSONObject boxJson = cloud.createClient().getBox(boxVersion);
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
        } while (Jenkins.getInstance().getNode(name) != null);
        
        return name;
    }
    
    private final String boxVersion;
    private String profileId;
    private final boolean singleUse;
    private String instanceUrl;
    private String instanceStatusMessage;
    private final int retentionTime;
    private final String cloudName;

    private transient boolean deletable;
    private final transient int launchTimeout;
    private final transient String environment;

    public ElasticBoxSlave(String profileId, String boxVersion, boolean singleUse, ElasticBoxCloud cloud) throws Descriptor.FormException, IOException {
        super(generateName(cloud, boxVersion), "", getRemoteFS(profileId, cloud), 1, Mode.EXCLUSIVE, "", 
                new JNLPLauncher(), new IdleTimeoutRetentionStrategy(cloud.getRetentionTime()), Collections.EMPTY_LIST);
        this.boxVersion = boxVersion;
        this.profileId = profileId;
        this.singleUse = singleUse;
        this.cloudName = cloud.name;
        this.retentionTime = cloud.getRetentionTime();
        this.launchTimeout = ElasticBoxSlaveHandler.TIMEOUT_MINUTES;
        this.environment = getNodeName().substring(0, 30);
    }
    
    public ElasticBoxSlave(SlaveConfiguration config, ElasticBoxCloud cloud) throws Descriptor.FormException, IOException {
        super(generateName(cloud, config.getBoxVersion()), config.getDescription(), 
            StringUtils.isBlank(config.getRemoteFS()) ? getRemoteFS(config.getProfile(), cloud) : config.getRemoteFS(), 
            config.getExecutors(), config.getMode(), config.getLabels(), new JNLPLauncher(), 
            new SlaveConfigurationRetentionStrategy(config, cloud), Collections.EMPTY_LIST);
        this.boxVersion = config.getBoxVersion();
        this.profileId = config.getProfile();
        this.singleUse = false;
        this.cloudName = cloud.name;
        this.retentionTime = config.getRetentionTime();
        this.launchTimeout = config.getLaunchTimeout();
        this.environment = config.getEnvironment();
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

    public void setDeletable(boolean inUse) {
        this.deletable = inUse;
    }

    public boolean isDeletable() {
        return deletable;
    }

    public ElasticBoxCloud getCloud() throws IOException {
        ElasticBoxCloud ebCloud = null;
        if (cloudName != null) {
            Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
            if (cloud instanceof ElasticBoxCloud) {
                ebCloud = (ElasticBoxCloud) cloud;
            } else {
                throw new IOException(MessageFormat.format("Cannot find any ElasticBox cloud with name ''{0}''", cloudName));
            }
        }
        
        return ebCloud != null ? ebCloud : ElasticBoxCloud.getInstance();
    }       
    
    public SlaveConfiguration getSlaveConfiguration() {
        if (getRetentionStrategy() instanceof SlaveConfigurationRetentionStrategy) {
            return ((SlaveConfigurationRetentionStrategy) getRetentionStrategy()).getSlaveConfiguration();
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

    public String getEnvironment() {
        return environment;
    }

    public int getRetentionTime() {
        return ((ElasticBoxRetentionStrategy) getRetentionStrategy()).getRetentionTime();
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public void terminate() throws IOException {
        checkInstanceReachable();
        Client client = getCloud().createClient();
        String instanceId = getInstanceId();
        try {
            client.terminate(instanceId);
        } catch (ClientException ex) {
            if (ex.getStatusCode() == HttpStatus.SC_CONFLICT) {
                client.forceTerminate(instanceId);
            }
        }
        ElasticBoxSlaveHandler.addToTerminatedQueue(this);
    }
    
    public void delete() throws IOException {
        checkInstanceReachable();
        getCloud().createClient().delete(getInstanceId());
    }
    
    public boolean isTerminated() throws IOException {
        checkInstanceReachable();
        JSONObject instance = getCloud().createClient().getInstance(getInstanceId());
        return Client.InstanceState.DONE.equals(instance.get("state")) && Client.TERMINATE_OPERATIONS.contains(instance.get("operation"));
    }
    
    public JSONObject getInstance() throws IOException {
        checkInstanceReachable();
        return getCloud().createClient().getInstance(getInstanceId());
    }
    
    public JSONObject getProfile() throws IOException {
        checkInstanceReachable();
        return getCloud().createClient().getProfile(getProfileId());
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
            throw new IOException(MessageFormat.format("The instance {0} has been created at a different ElasticBox endpoint than the currently configured one. Open {0} in a browser to terminate it.", instanceUrl));
        }        
    }
    
    private static String getRemoteFS(String profileId, ElasticBoxCloud cloud) throws IOException {
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONObject profile = client.getProfile(profileId);
        String boxId = profile.getJSONObject("box").getString("version");
        JSONObject box = (JSONObject) client.doGet(MessageFormat.format("/services/boxes/{0}", boxId), false);
        String service = box.getString("service");
        if ("Linux Compute".equals(service)) {
            return "/var/jenkins";
        } else if ("Windows Compute".equals(service)) {
            return "C:\\Jenkins";
        } else {
            throw new IOException(MessageFormat.format("Cannot create slave for profile '{0}' that belongs to box '{1}' with service '{2}'.",
                    profile.getString("name"), box.getString("name"), service));
        }
    }
    
    private static abstract class ElasticBoxRetentionStrategy extends RetentionStrategy<ElasticBoxComputer> {
        public abstract boolean shouldTerminate(ElasticBoxComputer computer);
        
        protected abstract int getRetentionTime();

        @Override
        public boolean isManualLaunchAllowed(ElasticBoxComputer c) {
            return false;
        }
        
        @Override
        public synchronized long check(ElasticBoxComputer computer) {
            if (shouldTerminate(computer)) {
                LOGGER.info(MessageFormat.format("Retention time of {0} minutes is elapsed for slave {1}. The computer is terminating", 
                        getRetentionTime(), computer.getSlave().getDisplayName()));
                computer.terminate();
            }
            
            return 1;
        }
                
    }
        
    private static class IdleTimeoutRetentionStrategy extends ElasticBoxRetentionStrategy {
        private final int retentionTime;

        @DataBoundConstructor
        public IdleTimeoutRetentionStrategy(int retentionTime) {
            this.retentionTime = retentionTime;
        }

        @Override
        public boolean shouldTerminate(ElasticBoxComputer computer) {
            // retention time 0 means being retained forever
            return getRetentionTime() > 0 && computer.getIdleTime() > TimeUnit.MINUTES.toMillis(getRetentionTime());            
        }

        @Override
        protected int getRetentionTime() {
            return retentionTime;
        }

    }
    
    private static final class SlaveConfigurationRetentionStrategy extends IdleTimeoutRetentionStrategy {
        private final String slaveConfigId;
        private final int minInstances;

        private transient String cloudName;
        
        SlaveConfigurationRetentionStrategy(SlaveConfiguration slaveConfig, ElasticBoxCloud cloud) {
            super(slaveConfig.getRetentionTime());
            this.slaveConfigId = slaveConfig.getId();
            this.minInstances = slaveConfig.getMinInstances();
            this.cloudName = cloud.name;
        }

        private SlaveConfiguration getSlaveConfiguration() {
            Cloud cloud = Jenkins.getInstance().getCloud(cloudName);            
            if (cloud instanceof ElasticBoxCloud) {
                return ((ElasticBoxCloud) cloud).getSlaveConfiguration(slaveConfigId);
            }
                
            return null;
        }

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
                try {
                    List<JSONObject> activeInstances = ElasticBoxSlaveHandler.getActiveInstances(computer.getSlave().getCloud());
                    if (activeInstances.size() <= getMinInstances()) {
                        return false;
                    }
                    
                    Set<String> configActiveInstanceIDs = new HashSet<String>();
                    for (Node node : Jenkins.getInstance().getNodes()) {
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
                    
                    if (configActiveInstanceIDs.contains(computer.getSlave().getInstanceId()) && 
                            instanceCount <= getMinInstances()) {
                        return false;
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
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
                
        @Initializer(before=InitMilestone.PLUGINS_STARTED)
        public static void addAliases() {
            Items.XSTREAM2.addCompatibilityAlias("com.elasticbox.jenkins.ElasticBoxSlave.RetentionStrategyImpl", 
                    IdleTimeoutRetentionStrategy.class);
        }        
    }
    
}
