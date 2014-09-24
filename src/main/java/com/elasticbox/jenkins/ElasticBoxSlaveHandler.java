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
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.SlaveInstance;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class ElasticBoxSlaveHandler extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxSlaveHandler.class.getName());
    
    public static final int TIMEOUT_MINUTES = Integer.getInteger("elasticbox.jenkins.deploymentTimeout", 60);
    private static final long TIMEOUT = TIMEOUT_MINUTES * 60000;
    private static final long RECURRENT_PERIOD = Long.getLong("elasticbox.jenkins.ElasticBoxSlaveHandler.recurrentPeriod", 10000);

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
    
    @Initializer(after = InitMilestone.COMPLETED)
    public static void tagSlaveInstances() throws IOException {
        LOGGER.info("Tagging slave instances");
        SlaveInstanceManager manager = new SlaveInstanceManager();
        for (JSONObject instance : manager.getInstances()) {
            ElasticBoxSlave slave = manager.getSlave(instance.getString("id"));
            tagSlaveInstance(instance, slave);
        }        
    }
    
    private static void tagSlaveInstance(JSONObject instance, ElasticBoxSlave slave) throws IOException {
        if (instance.getJSONArray("tags").contains(slave.getNodeName())) {
            return;
        }
        
        instance.getJSONArray("tags").add(slave.getNodeName());
        Client client = ClientCache.getClient(slave.getCloud().name);
        client.updateInstance(instance, null);
        LOGGER.fine(MessageFormat.format("Slave instance {0} has been tagged with slave name {1}",
                Client.getPageUrl(client.getEndpointUrl(), instance), slave.getNodeName()));        
    }
        
    public ElasticBoxSlaveHandler() {
        super("ElasticBox Slave Handler");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
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

    @Override
    public long getRecurrencePeriod() {
        return RECURRENT_PERIOD;
    }
    
    private void processSubmittedQueue(TaskListener listener) {
        boolean saveNeeded = false;
        for (Iterator<InstanceCreationRequest> iter = submittedQueue.iterator(); iter.hasNext();) {
            InstanceCreationRequest request = iter.next();
            try {
                if (request.monitor.isDone()) {
                    tagSlaveInstance(request.slave.getInstance(), request.slave);
                    
                    if (request.slave.getComputer() != null && request.slave.getComputer().isOnline()) {
                        request.slave.setInstanceStatusMessage(MessageFormat.format("Successfully deployed at <a href=\"{0}\">{0}</a>", 
                                request.slave.getInstancePageUrl()));
                        saveNeeded = true;
                        iter.remove();
                    } else if ((System.currentTimeMillis() - request.monitor.getCreationTime()) >= TIMEOUT) {
                        request.slave.setInUse(false);
                        iter.remove();
                        log(Level.SEVERE, MessageFormat.format("Slave agent {0} didn't contact after {1} minutes.", 
                                request.slave.getNodeName(), TIMEOUT_MINUTES), null, listener);
                    }
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
    
    private void removeSlave(ElasticBoxSlave slave) {
        try {            
            Jenkins.getInstance().removeNode(slave);
        } catch (IOException ex) {
            Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.SEVERE, 
                    MessageFormat.format("Error removing slave {0}", slave.getDisplayName()), ex);
        }        
    }
    
    private static Map<String, ElasticBoxSlave> getInstanceIdToSlaveMap() {
        Map<String, ElasticBoxSlave> instanceIdToSlaveMap = new HashMap<String, ElasticBoxSlave>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                final ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getInstanceUrl() != null) {
                    instanceIdToSlaveMap.put(slave.getInstanceId(), slave);
                }
            }
        }        
        return instanceIdToSlaveMap;
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
        Collection<ElasticBoxSlave> slaves = slaveInstanceManager.getSlaves();
        for (ElasticBoxSlave slave : slaves) {
            if (!isSlaveInQueue(slave, incomingQueue) && !slave.isInUse()) {
                slavesToRemove.add(slave);
            }
        }
        for (JSONObject instance : slaveInstanceManager.getInstances()) {
            String state = instance.getString("state");
            String instanceId = instance.getString("id");
            ElasticBoxSlave slave = slaveInstanceManager.getSlave(instanceId);
            if (Client.InstanceState.DONE.equals(state) && Client.TERMINATE_OPERATIONS.contains(instance.getString("operation"))) {
                addToTerminatedQueue(slave);
            } else if (Client.InstanceState.UNAVAILABLE.equals(state)) {
                Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.INFO, 
                        MessageFormat.format("The instance {0} is unavailable, it will be terminated.", slave.getInstancePageUrl()));
                slavesToRemove.add(slave);
            }                      
        }

        slavesToRemove.addAll(slaveInstanceManager.getSlavesWithoutInstance());
        
        return slavesToRemove;
    }
    
    private boolean purgeSlave(ElasticBoxSlave slave, TaskListener listener) {
        JSONObject instance = null;
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
                slave.getCloud().createClient().forceTerminate(instance.getString("id"));
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
    
    private void purgeSlaves(SlaveInstanceManager slaveInstanceManager, TaskListener listener) throws IOException {
        List<ElasticBoxSlave> slavesToRemove = collectSlavesToRemove(slaveInstanceManager);
        for (Iterator<ElasticBoxSlave> iter = terminatedSlaves.iterator(); iter.hasNext();) {
            ElasticBoxSlave slave = iter.next();
            if (purgeSlave(slave, listener)) {
                iter.remove();
                removeSlave(slave);                
            }
        }
        
        for (ElasticBoxSlave badSlave : slavesToRemove) {
            removeSlave(badSlave);
        }
    }
    
    private static boolean isSlaveInQueue(ElasticBoxSlave slave, Queue<InstanceCreationRequest> queue) {
        for (InstanceCreationRequest request : queue) {
            if (request.slave == slave) {
                return true;
            }
        }
        return false;
    }
    
    private void log(String message, TaskListener listener) {
        this.log(Level.INFO, message, null, listener);
    }
    
    private void log(Level level, String message, Throwable exception, TaskListener listener) {
        listener.getLogger().println(message);
        LOGGER.log(level, message, exception);       
    }
    
    private void deployInstance(InstanceCreationRequest request) throws IOException {
        ElasticBoxCloud cloud = request.slave.getCloud();        
        Client ebClient = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONObject profile = ebClient.getProfile(request.slave.getProfileId());     
        JSONArray variables = SlaveInstance.createJenkinsVariables(ebClient, Jenkins.getInstance().getRootUrl(), request.slave);
        String scope = variables.getJSONObject(0).getString("scope");
        SlaveConfiguration slaveConfig = request.slave.getSlaveConfiguration();
        if (slaveConfig != null && slaveConfig.getVariables() != null) {
            JSONArray configuredVariables = VariableResolver.parseVariables(slaveConfig.getVariables());
            for (int i = 0; i < configuredVariables.size(); i++) {
                JSONObject variable = configuredVariables.getJSONObject(i);
                if (!scope.equals(variable.getString("scope")) || !SlaveInstance.REQUIRED_VARIABLES.contains(variable.getString("name"))) {
                    variables.add(variable);
                }
            }
        }
        IProgressMonitor monitor = ebClient.deploy(request.slave.getBoxVersion(), request.slave.getProfileId(), 
                profile.getString("owner"), request.slave.getEnvironment(), 1, variables);
        request.slave.setInstanceUrl(monitor.getResourceUrl());
        request.slave.setInstanceStatusMessage(MessageFormat.format("Submitted request to deploy instance <a href=\"{0}\">{0}</a>", 
                request.slave.getInstancePageUrl()));
        request.monitor.setMonitor(monitor);
        submittedQueue.add(request);
    }
    
    private Map<SlaveConfiguration, Integer> countSlavesPerConfiguration() {
        Map<SlaveConfiguration, Integer> slaveConfigToSlaveCountMap = new HashMap<SlaveConfiguration, Integer>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                SlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
                if (slaveConfig != null) {
                    Integer slaveCount = slaveConfigToSlaveCountMap.get(slaveConfig);
                    slaveConfigToSlaveCountMap.put(slaveConfig, slaveCount == null ? 1 : ++slaveCount);
                }
            }
        }  
        return slaveConfigToSlaveCountMap;
    }
    
    private void launchMinimumSlaves(ElasticBoxCloud cloud, Map<SlaveConfiguration, Integer> slaveConfigToSlaveCountMap) 
            throws IOException {
        for (SlaveConfiguration slaveConfig : cloud.getSlaveConfigurations()) {
            if (slaveConfig.getMinInstances() > 0) {
                Integer slaveCount = slaveConfigToSlaveCountMap.get(slaveConfig);
                if (slaveCount == null || slaveConfig.getMinInstances() > slaveCount) {
                    try {
                        ElasticBoxSlave slave = new ElasticBoxSlave(slaveConfig, cloud);
                        slave.setInUse(true);
                        Jenkins.getInstance().addNode(slave);
                        ElasticBoxSlaveHandler.submit(slave);
                        break;
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (Descriptor.FormException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }
        }
    }

    private void launchMinimumSlaves() throws IOException {
        Map<SlaveConfiguration, Integer> slaveConfigToSlaveCountMap = countSlavesPerConfiguration();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                launchMinimumSlaves((ElasticBoxCloud) cloud, slaveConfigToSlaveCountMap);
            }
        }
    }
    
}
