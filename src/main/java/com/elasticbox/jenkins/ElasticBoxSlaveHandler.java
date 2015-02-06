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
import com.elasticbox.IProgressMonitor;
import static com.elasticbox.jenkins.ElasticBoxExecutor.threadPool;
import com.elasticbox.jenkins.util.SlaveInstance;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class ElasticBoxSlaveHandler extends ElasticBoxExecutor.Workload {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxSlaveHandler.class.getName());
    
    public static final int TIMEOUT_MINUTES = Integer.getInteger("elasticbox.jenkins.deploymentTimeout", 60);

    private static class InstanceCreationRequest {
        private final ElasticBoxSlave slave;
        private final LaunchSlaveProgressMonitor monitor;

        public InstanceCreationRequest(ElasticBoxSlave slave) {
            this.slave = slave;
            monitor = new LaunchSlaveProgressMonitor(slave);
        }
        
    }
    
    private static final Queue<InstanceCreationRequest> incomingQueue = new ConcurrentLinkedQueue<InstanceCreationRequest>();
    private static final Queue<InstanceCreationRequest> submittedQueue = new ConcurrentLinkedQueue<InstanceCreationRequest>();
    private static final Queue<ElasticBoxSlave> terminatedSlaves = new ConcurrentLinkedQueue<ElasticBoxSlave>();
    
    public static final ElasticBoxSlaveHandler getInstance() {
        return Jenkins.getInstance().getExtensionList(ElasticBoxExecutor.Workload.class).get(ElasticBoxSlaveHandler.class);
    }
    
    public static final IProgressMonitor submit(ElasticBoxSlave slave) {
        InstanceCreationRequest request = new InstanceCreationRequest(slave);
        incomingQueue.add(request);
        return request.monitor;
    }
    
    public static final boolean isSubmitted(ElasticBoxSlave slave) {
        for (InstanceCreationRequest request : incomingQueue) {
            if (request.slave == slave) {
                return true;
            }
        }
        return false;
    }
    
    public static final void addToTerminatedQueue(ElasticBoxSlave slave) {
        if (!terminatedSlaves.contains(slave)) {
            terminatedSlaves.add(slave);
        }        
        for (Iterator<InstanceCreationRequest> iter = submittedQueue.iterator(); iter.hasNext();) {
            InstanceCreationRequest request = iter.next();
            if (request.slave == slave) {
                iter.remove();
            }
        }
    }
    
    public static List<JSONObject> getActiveInstances(ElasticBoxCloud cloud) throws IOException {
        return new SlaveInstanceManager().getInstances(cloud);
    }
    
    public void tagSlaveInstance(JSONObject instance, ElasticBoxSlave slave) throws IOException {
        if (instance.getJSONArray("tags").contains(slave.getNodeName())) {
            return;
        }
        
        instance.getJSONArray("tags").add(slave.getNodeName());
        Client client = slave.getCloud().getClient();
        client.updateInstance(instance);
        log(Level.FINE, MessageFormat.format("Slave instance {0} has been tagged with slave name {1}",
                Client.getPageUrl(client.getEndpointUrl(), instance), slave.getNodeName()));        
    }

    @Override
    protected ElasticBoxExecutor.ExecutionType getExecutionType() {
        return ElasticBoxExecutor.ExecutionType.SYNC;
    }
    
    @Override
    protected void execute(TaskListener listener) throws IOException {
        SlaveInstanceManager slaveInstanceManager = new SlaveInstanceManager();
        purgeSlaves(slaveInstanceManager, listener);    
        Map<ElasticBoxCloud, Integer> cloudToMaxNewInstancesMap = new HashMap<ElasticBoxCloud, Integer>();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                ElasticBoxCloud ebCloud = (ElasticBoxCloud) cloud;
                List<JSONObject> instances = slaveInstanceManager.getInstances(ebCloud);
                cloudToMaxNewInstancesMap.put(ebCloud, ebCloud.getMaxInstances() - instances.size());
            }
        }
        
        boolean saveConfig = false;
        for (InstanceCreationRequest request = incomingQueue.poll(); request != null; request = incomingQueue.poll()) {
            ElasticBoxCloud cloud = request.slave.getCloud();
            int maxNewInstances = cloudToMaxNewInstancesMap.get(cloud);
            if (maxNewInstances > 0) {
                try {
                    deployInstance(request);
                    saveConfig = true;
                    cloudToMaxNewInstancesMap.put(cloud, maxNewInstances--);
                    log(MessageFormat.format("Deloying a new instance for slave {0}", 
                            request.slave.getDisplayName()), listener);                    
                } catch (IOException ex) {
                    log(Level.SEVERE, MessageFormat.format("Error deloying a new instance for slave {0}", 
                            request.slave.getDisplayName()), ex, listener);
                    request.monitor.setMonitor(IProgressMonitor.DONE_MONITOR);
                    removeSlave(request.slave);
                }
            } else {
                log(Level.WARNING, MessageFormat.format("Max number of ElasticBox instances has been reached for {0}", cloud.getDisplayName()), null, listener);
                request.monitor.setMonitor(IProgressMonitor.DONE_MONITOR);
                removeSlave(request.slave);
            }            
        }

        if (saveConfig) {
            Jenkins.getInstance().save();
        }
        
        processSubmittedQueue(listener);
                
        launchMinimumSlaves();
    }

    private boolean removeSlaveIfLaunchTimedOut(InstanceCreationRequest request, TaskListener listener) {
        if (request.monitor.getLaunchTime() > 0) {
            long launchDuration = System.currentTimeMillis() - request.monitor.getLaunchTime();
            if (launchDuration >= TimeUnit.MINUTES.toMillis(request.slave.getLaunchTimeout())) {                        
                request.slave.markForTermination();
                log(Level.SEVERE, MessageFormat.format("Slave agent {0} did not contact after {1} minutes.", 
                        request.slave.getNodeName(), TimeUnit.MILLISECONDS.toMinutes(launchDuration)), null, listener);
                return true;
            }  
        }
        return false;
    }
    
    private void processSubmittedQueue(TaskListener listener) {
        boolean saveNeeded = false;
        for (Iterator<InstanceCreationRequest> iter = submittedQueue.iterator(); iter.hasNext();) {
            InstanceCreationRequest request = iter.next();
            try {
                if (request.monitor.isDone()) {
                    if (request.slave.getComputer() != null && request.slave.getComputer().isOnline()) {
                        request.slave.setInstanceStatusMessage(MessageFormat.format("Successfully deployed at <a href=\"{0}\">{0}</a>", 
                                request.slave.getInstancePageUrl()));
                        saveNeeded = true;
                        iter.remove();
                    } else { 
                        if (removeSlaveIfLaunchTimedOut(request, listener)) {
                            iter.remove();
                        }
                    }
                } else {
                    removeSlaveIfLaunchTimedOut(request, listener);
                }
            } catch (IProgressMonitor.IncompleteException ex) {
                log(Level.SEVERE, ex.getMessage(), ex, listener);
                iter.remove();
            } catch (IOException ex) {
                log(Level.SEVERE, ex.getMessage(), ex, listener);
            }
        }
        if (saveNeeded) {
            try {
                Jenkins.getInstance().save();
            } catch (IOException ex) {
                Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.SEVERE, "Error saving configuration", ex);
            }
        }
    }
    
    private static void removeSlave(ElasticBoxSlave slave) {     
        try {            
            Jenkins.getInstance().removeNode(slave);
        } catch (IOException ex) {
            Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.SEVERE, 
                    MessageFormat.format("Error removing slave {0}", slave.getDisplayName()), ex);
        }        
    }
    
    /**
     * Collects inactive or invalid slaves that can be removed.
     * 
     * @param slavesToRemove a list to be filled with inactive or invalid slaves that can be removed
     * @return a list of slaves to remove
     * @throws IOException 
     */
    private static List<ElasticBoxSlave> collectSlavesToRemove(SlaveInstanceManager slaveInstanceManager) throws IOException {
        List<ElasticBoxSlave> slavesToRemove = new ArrayList<ElasticBoxSlave>();
        for (JSONObject instance : slaveInstanceManager.getInstances()) {
            String state = instance.getString("state");
            String instanceId = instance.getString("id");
            ElasticBoxSlave slave = slaveInstanceManager.getSlave(instanceId);
            if (Client.InstanceState.DONE.equals(state) && Client.TERMINATE_OPERATIONS.contains(instance.getString("operation"))) {
                addToTerminatedQueue(slave);
            } else if (Client.InstanceState.UNAVAILABLE.equals(state) && !slave.getComputer().isTemporarilyOffline()) {
                Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.INFO, 
                        MessageFormat.format("The instance {0} is unavailable, it will be terminated.", slave.getInstancePageUrl()));
                slavesToRemove.add(slave);
            }                      
        }

        slavesToRemove.addAll(slaveInstanceManager.getSlavesWithoutInstance());
        
        return slavesToRemove;
    }
    
    private boolean purgeSlave(ElasticBoxSlave slave, TaskListener listener) {
        JSONObject instance;
        try {
            instance = slave.getInstance();
        } catch (IOException ex) {
            if (ex instanceof ClientException && ((ClientException) ex).getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return true;
            }
            log(Level.SEVERE, MessageFormat.format("Error fetching the instance data of ElasticBox slave {0}", slave.getDisplayName()), ex, listener);
            return false;
        }
        String state = instance.getString("state");
        if (Client.InstanceState.UNAVAILABLE.equals(state)) {
            try {
                slave.getCloud().getClient().forceTerminate(instance.getString("id"));
            } catch (IOException ex) {
                log(Level.SEVERE, MessageFormat.format("Error force-terminating the instance of ElasticBox slave {0}", slave.getDisplayName()), ex, listener);
            }
            return false;
        }
        if (!Client.InstanceState.DONE.equals(state)) {
            return false;
        }

        try  {
            try {
                slave.delete();
            } catch (ClientException ex) {
                if (ex.getStatusCode() == HttpStatus.SC_CONFLICT) {
                    return false;
                } else if (ex.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                    throw ex;
                }
            }
            return true;
        } catch (IOException ex) {
            log(Level.SEVERE, MessageFormat.format("Error deleting ElasticBox slave {0}", slave.getDisplayName()), ex, listener);
            return false;
        }        
    }
    
    private void purgeSlaves(SlaveInstanceManager slaveInstanceManager, final TaskListener listener) throws IOException {
        List<ElasticBoxSlave> slavesToRemove = collectSlavesToRemove(slaveInstanceManager);
        
        // terminate slaves that are marked as deletable
        Collection<ElasticBoxSlave> slaves = slaveInstanceManager.getSlaves();
        for (ElasticBoxSlave slave : slaves) {
            if (slave.isDeletable() && slaveInstanceManager.getInstance(slave) != null) {                
                final ElasticBoxSlave slaveToTerminate = slave;
                threadPool.submit(new Runnable() {
                    public void run() {
                        try {
                            slaveToTerminate.terminate();
                        } catch (IOException ex) {
                            log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", slaveToTerminate.getDisplayName()), ex);
                        }
                    }
                });
            }
        }
        
        // remove terminated slaves
        for (Iterator<ElasticBoxSlave> iter = terminatedSlaves.iterator(); iter.hasNext();) {
            final ElasticBoxSlave slave = iter.next();
            threadPool.submit(new Runnable() {

                @Override
                public void run() {
                    if (purgeSlave(slave, listener)) {
                        terminatedSlaves.remove(slave);
                        removeSlave(slave);                
                    }
                }
                
            });
        }
        
        // remove bad slaves
        for (ElasticBoxSlave slave : slavesToRemove) {
            final ElasticBoxSlave badSlave = slave;
            threadPool.submit(new Runnable() {

                @Override
                public void run() {
                    removeSlave(badSlave);
                }
                
            });
            
        }
    }
    
    private void deployInstance(InstanceCreationRequest request) throws IOException {
        ElasticBoxCloud cloud = request.slave.getCloud();        
        Client ebClient = cloud.getClient();
        JSONObject profile = ebClient.getProfile(request.slave.getProfileId());     
        JSONArray variables = SlaveInstance.createJenkinsVariables(ebClient, request.slave);
        JSONObject jenkinsVariable = variables.getJSONObject(0);
        String scope = jenkinsVariable.containsKey("scope") ? jenkinsVariable.getString("scope") : StringUtils.EMPTY;
        AbstractSlaveConfiguration slaveConfig = request.slave.getSlaveConfiguration();
        if (slaveConfig != null && slaveConfig.getVariables() != null) {
            JSONArray configuredVariables = VariableResolver.parseVariables(slaveConfig.getVariables());
            for (int i = 0; i < configuredVariables.size(); i++) {
                JSONObject variable = configuredVariables.getJSONObject(i);
                if (!scope.equals(variable.getString("scope")) || 
                        !SlaveInstance.REQUIRED_VARIABLES.contains(variable.getString("name"))) {
                    variables.add(variable);
                }
            }
        }
        LOGGER.info(MessageFormat.format("Deploying box {0}", ebClient.getBoxPageUrl(request.slave.getBoxVersion())));
        IProgressMonitor monitor = ebClient.deploy(request.slave.getBoxVersion(), request.slave.getProfileId(), 
                profile.getString("owner"), request.slave.getEnvironment(), 
                Collections.singletonList(request.slave.getNodeName()), 1, variables, null, null);
        request.slave.setInstanceUrl(monitor.getResourceUrl());
        request.slave.setInstanceStatusMessage(MessageFormat.format("Submitted request to deploy instance <a href=\"{0}\">{0}</a>", 
                request.slave.getInstancePageUrl()));
        request.monitor.setMonitor(monitor);
        request.monitor.setLaunched();
        submittedQueue.add(request);
    }
    
    private Map<AbstractSlaveConfiguration, Integer> countSlavesPerConfiguration() {
        Map<AbstractSlaveConfiguration, Integer> slaveConfigToSlaveCountMap = new HashMap<AbstractSlaveConfiguration, Integer>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
                if (slaveConfig != null) {
                    Integer slaveCount = slaveConfigToSlaveCountMap.get(slaveConfig);
                    slaveConfigToSlaveCountMap.put(slaveConfig, slaveCount == null ? 1 : ++slaveCount);
                }
            }
        }  
        return slaveConfigToSlaveCountMap;
    }
    
    private void launchMinimumSlaves(ElasticBoxCloud cloud, Map<AbstractSlaveConfiguration, Integer> slaveConfigToSlaveCountMap) 
            throws IOException {
        for (SlaveConfiguration slaveConfig : cloud.getSlaveConfigurations()) {
            if (slaveConfig.getMinInstances() > 0) {
                Integer slaveCount = slaveConfigToSlaveCountMap.get(slaveConfig);
                if (slaveCount == null || slaveConfig.getMinInstances() > slaveCount) {
                    try {
                        ElasticBoxSlave slave = new ElasticBoxSlave(slaveConfig, cloud);
                        Jenkins.getInstance().addNode(slave);
                        ElasticBoxSlaveHandler.submit(slave);
                        break;
                    } catch (IOException ex) {
                        log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (Descriptor.FormException ex) {
                        log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }
        }
    }

    private void launchMinimumSlaves() throws IOException {
        Map<AbstractSlaveConfiguration, Integer> slaveConfigToSlaveCountMap = countSlavesPerConfiguration();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                launchMinimumSlaves((ElasticBoxCloud) cloud, slaveConfigToSlaveCountMap);
            }
        }
    }
    
}
