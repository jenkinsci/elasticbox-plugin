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
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.labels.LabelAtom;
import hudson.slaves.ComputerListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    private final String boxVersion;
    private String profileId;
    private final boolean singleUse;
    private String instanceUrl;
    private String instanceStatusMessage;
    private final int retentionTime;

    private transient boolean inUse;
    private transient ElasticBoxCloud cloud;
    private final transient int launchTimeout;
    private final transient String environment;

    public ElasticBoxSlave(String profileId, String boxVersion, boolean singleUse, ElasticBoxCloud cloud) throws Descriptor.FormException, IOException {
        super(UUID.randomUUID().toString(), "", getRemoteFS(profileId, cloud), 1, Mode.EXCLUSIVE, "", 
                new JNLPLauncher(), new RetentionStrategyImpl(cloud.getRetentionTime()));
        this.boxVersion = boxVersion;
        this.profileId = profileId;
        this.singleUse = singleUse;
        this.cloud = cloud;
        this.retentionTime = cloud.getRetentionTime();
        this.launchTimeout = ElasticBoxSlaveHandler.TIMEOUT_MINUTES;
        this.environment = getNodeName().substring(0, 30);
    }
    
    public ElasticBoxSlave(SlaveConfiguration config, ElasticBoxCloud cloud) throws Descriptor.FormException, IOException {
        super(UUID.randomUUID().toString(), config.getDescription(), 
            StringUtils.isBlank(config.getRemoteFS()) ? getRemoteFS(config.getProfile(), cloud) : config.getRemoteFS(), 
            config.getExecutors(), config.getMode(), config.getLabels(), new JNLPLauncher(), 
            RetentionStrategy.INSTANCE, Collections.EMPTY_LIST);
        this.boxVersion = config.getBoxVersion();
        this.profileId = config.getProfile();
        this.singleUse = false;
        this.cloud = cloud;
        this.retentionTime = config.getRetentionTime();
        this.launchTimeout = config.getLaunchTimeout();
        this.environment = config.getEnvironment();
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

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setCloud(ElasticBoxCloud cloud) {
        this.cloud = cloud;
    }

    public ElasticBoxCloud getCloud() {
        return cloud != null ? cloud : ElasticBoxCloud.getInstance();
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
        return retentionTime;
    }

    public String getBoxVersion() {
        return boxVersion;
    }
    
    public boolean canTerminate() throws IOException {
        if (retentionTime == 0) {
            return false;
        }
        
        ElasticBoxCloud ebCloud = getCloud();
        boolean canTerminate = ebCloud != null && instanceUrl != null &&
            instanceUrl.startsWith(ebCloud.getEndpointUrl()) &&
            ((ElasticBoxComputer) getComputer()).getIdleTime() > TimeUnit.MINUTES.toMillis(retentionTime);
        
        if (canTerminate) {
            SlaveComputer computer = getComputer();
            if (computer != null) {
                for (Object build : computer.getBuilds()) {
                    if (build instanceof AbstractBuild && ((AbstractBuild) build).isBuilding()) {
                        canTerminate = false;
                        break;
                    }
                }
            }
        }
        
        return canTerminate;
    }

    public void terminate() throws IOException {
        checkInstanceReachable();
        createClient().terminate(getInstanceId());
        ElasticBoxSlaveHandler.addToTerminatedQueue(this);
    }
    
    public void delete() throws IOException {
        checkInstanceReachable();
        createClient().delete(getInstanceId());
    }
    
    public boolean isTerminated() throws IOException {
        checkInstanceReachable();
        JSONObject instance = createClient().getInstance(getInstanceId());
        return Client.InstanceState.DONE.equals(instance.get("state")) && Client.TERMINATE_OPERATIONS.contains(instance.get("operation"));
    }
    
    public JSONObject getInstance() throws IOException {
        checkInstanceReachable();
        return createClient().getInstance(getInstanceId());
    }
    
    public JSONObject getProfile() throws IOException {
        checkInstanceReachable();
        return (JSONObject) createClient().doGet(MessageFormat.format("{0}/services/profiles/{1}", getCloud().getEndpointUrl(), getProfileId()), false);
    }  
    
    private Client createClient() {
        ElasticBoxCloud ebCloud = getCloud();
        return new Client(ebCloud.getEndpointUrl(), ebCloud.getUsername(), ebCloud.getPassword());        
    }
    
    private void checkInstanceReachable() throws IOException {
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
        
    private static final class ElasticBoxComputer extends SlaveComputer {
        private boolean terminateOnOffline = false;

        public ElasticBoxComputer(ElasticBoxSlave slave) {
            super(slave);
        }

        @Override
        public Future<?> disconnect(OfflineCause cause) {
            boolean online = isOnline();
            Future<?> future = super.disconnect(cause);

            if (cause instanceof OfflineCause.SimpleOfflineCause && 
                    ((OfflineCause.SimpleOfflineCause) cause).description.toString().equals(Messages._Hudson_NodeBeingRemoved().toString())) {
                ElasticBoxSlave slave = getNode();
                // remove any pending launches
                for (LabelAtom label : ElasticBoxLabelFinder.INSTANCE.findLabels(slave)) {
                    for (NodeProvisioner.PlannedNode plannedNode : label.nodeProvisioner.getPendingLaunches()) {
                        if (plannedNode.displayName.equals(slave.getNodeName())) {
                            plannedNode.future.cancel(false);
                        }
                    }
                }
                if (online) {
                    terminateOnOffline = true;
                } else {
                    terminate();
                }
            }

            return future;
        }

        @Override
        public ElasticBoxSlave getNode() {
            return (ElasticBoxSlave) super.getNode();
        }
        
        public long getIdleTime() {
            return isIdle() && isOnline() ? System.currentTimeMillis() - getIdleStartMilliseconds() : 0;
        }
        
        private void terminate() {
            try {
                ElasticBoxSlave slave = getNode();
                slave.checkInstanceReachable();
                try {
                    slave.terminate();
                } catch (ClientException ex) {
                    if (ex.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                        LOGGER.log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", getDisplayName()), ex);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", getDisplayName()), ex);
                }                        
            } catch (IOException ex) {                    
            }                
        }
        
    }

    private static final class RetentionStrategyImpl extends RetentionStrategy<ElasticBoxComputer> {
        private final int retentionTime;

        @DataBoundConstructor
        public RetentionStrategyImpl(int retentionTime) {
            this.retentionTime = retentionTime;
        }
        
        @Override
        public synchronized long check(ElasticBoxComputer c) {
            if (retentionTime == 0) {
                // retention time 0 means being retained forever
                return 1;
            }
            
            if (c.getIdleTime() > TimeUnit.MINUTES.toMillis(retentionTime)) {
                LOGGER.info(MessageFormat.format("Retention time of {0} minutes is elapsed for computer {1}. The computer is terminating", retentionTime, c.getName()));
                c.terminate();
            }
            
            return 1;
        }

    }
    

    @Extension
    public static final class ComputerListenerImpl extends ComputerListener {

        @Override
        public void onOffline(Computer c) {
            if (c instanceof ElasticBoxComputer) {
                ElasticBoxComputer ebComputer = (ElasticBoxComputer) c;
                if (ebComputer.terminateOnOffline) {
                    ebComputer.terminate();
                }
            }
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
                
    }
    
}
