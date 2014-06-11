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
import hudson.slaves.JNLPLauncher;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.kohsuke.stapler.StaplerRequest;


/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxSlave extends Slave {
    private String profileId;
    private boolean singleUse;
    private String instanceUrl;
    private String instanceStatusMessage;
    private long idleStartTime;

    private transient boolean inUse;
    private transient ElasticBoxCloud cloud;

    public ElasticBoxSlave(String name, boolean singleUse) throws Descriptor.FormException, IOException {
        this(name, singleUse, "/var/jenkins", 1);
    }
    
    public ElasticBoxSlave(String name, boolean singleUse, String remoteFS, int numExecutors) throws Descriptor.FormException, IOException {
        super(name, "", remoteFS, numExecutors, Mode.EXCLUSIVE, "", new JNLPLauncher(), RetentionStrategy.INSTANCE);
        this.singleUse = singleUse;
        this.idleStartTime = System.currentTimeMillis();
    }

    @Override
    public Computer createComputer() {
        return new SlaveComputer(this) {

            @Override
            public Future<?> disconnect(OfflineCause cause) {
                if (cause instanceof OfflineCause.SimpleOfflineCause && 
                        ((OfflineCause.SimpleOfflineCause) cause).description.toString().equals(Messages._Hudson_NodeBeingRemoved().toString())) {
                    try {
                        checkInstanceReachable();
                        try {
                            ElasticBoxSlave.this.terminate();
                        } catch (ClientException ex) {
                            if (ex.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                                Logger.getLogger(ElasticBoxSlave.class.getName()).log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", getDisplayName()), ex);
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(ElasticBoxSlave.class.getName()).log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", getDisplayName()), ex);
                        }                        
                    } catch (IOException ex) {                        
                    }
                }
                return super.disconnect(cause); //To change body of generated methods, choose Tools | Templates.
            }
            
        };
    }
    
    

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }    

    public String getInstancePageUrl(String instanceId) throws IOException {
        checkInstanceReachable();
        return MessageFormat.format("{0}/#/instances/{1}/i", getCloud().getEndpointUrl(), instanceId);
    }        
        
    public String getInstanceId() {
        return instanceUrl != null ? instanceUrl.substring(instanceUrl.lastIndexOf('/') + 1) : null;
    }

    public boolean isSingleUse() {
        return singleUse;
    }   

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
        if (!inUse) {
            this.idleStartTime = System.currentTimeMillis();
        }
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

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }
    
    public boolean canTerminate() throws IOException {
        ElasticBoxCloud ebCloud = getCloud();
        boolean canTerminate = ebCloud != null && instanceUrl != null &&
            instanceUrl.startsWith(ebCloud.getEndpointUrl()) &&
            (System.currentTimeMillis() - idleStartTime) > (ebCloud.getRetentionTime() * 60000);
        
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
