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
import com.elasticbox.jenkins.util.SlaveInstance;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.time.StopWatch;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class ElasticBoxSlaveHandler extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(InstanceCreator.class.getName());
    
    public static final int TIMEOUT_MINUTES = Integer.getInteger("elasticbox.jenkins.deploymentTimeout", 60);
    private static final long TIMEOUT = TIMEOUT_MINUTES * 60000;
    private static final long RECURRENT_PERIOD = Long.getLong("elasticbox.jenkins.ElasticBoxSlaveHandler.recurrentPeriod", 10000);
    private static final IProgressMonitor DONE_MONITOR = new IProgressMonitor() {

        public String getResourceUrl() {
            return null;
        }

        public boolean isDone() throws IProgressMonitor.IncompleteException, IOException {
            return true;
        }

        public long getCreationTime() {
            throw new UnsupportedOperationException();
        }

        public void waitForDone(int timeout) throws IProgressMonitor.IncompleteException, IOException {
        }

        public boolean isDone(JSONObject instance) throws IProgressMonitor.IncompleteException, IOException {
            return true;
        }
    };

    private static class InstanceCreationRequest {
        private final ElasticBoxSlave slave;
        private final ProgressMonitorWrapper monitor;

        public InstanceCreationRequest(ElasticBoxSlave slave) {
            this.slave = slave;
            monitor = new ProgressMonitorWrapper(slave);
        }
        
    }
    
    private static class ProgressMonitorWrapper implements IProgressMonitor {
        private final Object waitLock = new Object();
        private final long creationTime;
        private final ElasticBoxSlave slave;
        private IProgressMonitor monitor;

        public ProgressMonitorWrapper(ElasticBoxSlave slave) {
            creationTime = System.currentTimeMillis();
            this.slave = slave;
        }
        
        public String getResourceUrl() {
            return monitor != null ? monitor.getResourceUrl() : null;
        }

        public boolean isDone() throws IncompleteException, IOException {
            return monitor != null ? monitor.isDone() : false;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public void setMonitor(IProgressMonitor monitor) {
            this.monitor = monitor;
            synchronized (waitLock) {
                waitLock.notifyAll();
            }
        }
        
        private void wait(Callable<Boolean> condition, long timeout) throws Exception {
            long startTime = System.currentTimeMillis();
            long remainingTime = timeout;
            while(remainingTime > 0 && condition.call()) {
                synchronized (waitLock) {
                    try {
                        waitLock.wait(remainingTime);
                    } catch (InterruptedException ex) {
                    }
                }
                long currentTime = System.currentTimeMillis();
                remainingTime = remainingTime - (currentTime - startTime);
                startTime = currentTime;                
            }            
        }
        
        public void waitForDone(int timeout) throws IncompleteException, IOException {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            long timeoutMiliseconds = timeout * 60000;
            long remainingTime = timeoutMiliseconds;
            try {
                wait(new Callable<Boolean>() {
                    
                    public Boolean call() throws Exception {
                        return monitor == null;
                    }
                }, timeoutMiliseconds);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
            
            if (monitor == DONE_MONITOR) {
                return;
            }
            
            remainingTime = remainingTime - stopWatch.getTime();
            if (monitor != null && remainingTime > 0) {
                monitor.waitForDone(Math.round(remainingTime / 60000));
            }
            
            remainingTime = remainingTime - stopWatch.getTime();
            if (remainingTime > 0) {
                try {
                    wait(new Callable<Boolean>() {
                        
                        public Boolean call() throws Exception {
                            SlaveComputer computer = slave.getComputer();
                            return computer != null && computer.isOffline();
                        }
                    }, remainingTime);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        }

        public boolean isDone(JSONObject instance) throws IncompleteException, IOException {
            return monitor != null ? monitor.isDone(instance) : false;
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
    
    public static JSONArray getActiveInstances(ElasticBoxCloud cloud) throws IOException {
        JSONArray activeInstances = collectSlavesToRemove(new ArrayList<ElasticBoxSlave>()).get(cloud);
        return activeInstances != null ? activeInstances : new JSONArray();
    }
        
    public ElasticBoxSlaveHandler() {
        super("ElasticBox Slave Handler");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        Map<ElasticBoxCloud, JSONArray> cloudToActiveInstancesMap = purgeSlaves(listener);    
        Map<ElasticBoxCloud, Integer> cloudToMaxNewInstancesMap = new HashMap<ElasticBoxCloud, Integer>();
        for (Map.Entry<ElasticBoxCloud, JSONArray> entry : cloudToActiveInstancesMap.entrySet()) {
            ElasticBoxCloud cloud = entry.getKey();
            cloudToMaxNewInstancesMap.put(cloud, cloud.getMaxInstances() - entry.getValue().size());
        }
        
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud && !cloudToMaxNewInstancesMap.containsKey(cloud)) {
                ElasticBoxCloud ebCloud = (ElasticBoxCloud) cloud;
                cloudToMaxNewInstancesMap.put(ebCloud, ebCloud.getMaxInstances());
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
                    request.monitor.setMonitor(DONE_MONITOR);
                    removeSlave(request.slave);
                }
            } else {
                log(Level.WARNING, MessageFormat.format("Max number of ElasticBox instances has been reached for {0}", cloud.getDisplayName()), null, listener);
                request.monitor.setMonitor(DONE_MONITOR);
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
                    if (request.slave.getComputer() != null && request.slave.getComputer().isOnline()) {
                        request.slave.setInstanceStatusMessage(MessageFormat.format("Successfully deployed at {0}", 
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
    
    /**
     * Collects inactive or invalid slaves that can be removed.
     * 
     * @param slavesToRemove a list to be filled with inactive or invalid slaves that can be removed
     * @return a list of active instances
     * @throws IOException 
     */
    private static Map<ElasticBoxCloud, JSONArray> collectSlavesToRemove(List<ElasticBoxSlave> slavesToRemove) throws IOException {
        Map<String, ElasticBoxSlave> instanceIdToSlaveMap = new HashMap<String, ElasticBoxSlave>();
        Map<ElasticBoxCloud, JSONArray> cloudToInstancesMap = new HashMap<ElasticBoxCloud, JSONArray>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave && !isSlaveInQueue((ElasticBoxSlave) node, incomingQueue)) {
                final ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getInstanceUrl() != null) {
                    instanceIdToSlaveMap.put(slave.getInstanceId(), slave);
                } else if (!slave.isInUse()) {
                    slavesToRemove.add(slave);
                }
            }
        }    
        
        if (!instanceIdToSlaveMap.isEmpty()) {
            Map<ElasticBoxCloud, List<String>> cloudToInstanceIDsMap = new HashMap<ElasticBoxCloud, List<String>>();
            for (Map.Entry<String, ElasticBoxSlave> entry : instanceIdToSlaveMap.entrySet()) {
                ElasticBoxSlave slave = entry.getValue();
                ElasticBoxCloud cloud = slave.getCloud();
                List<String> instanceIDs = cloudToInstanceIDsMap.get(cloud);
                if (instanceIDs == null) {
                    instanceIDs = new ArrayList<String>();
                    cloudToInstanceIDsMap.put(cloud, instanceIDs);
                }
                instanceIDs.add(slave.getInstanceId());
            }
            
            for (Map.Entry<ElasticBoxCloud, List<String>> entry : cloudToInstanceIDsMap.entrySet()) {
                ElasticBoxCloud cloud = entry.getKey();
                cloudToInstancesMap.put(cloud, cloud.createClient().getInstances(entry.getValue()));
            }
            
            for (JSONArray instances : cloudToInstancesMap.values()) {
                for (Iterator iter = instances.iterator(); iter.hasNext();) {
                    JSONObject instance = (JSONObject) iter.next();
                    String state = instance.getString("state");
                    String instanceId = instance.getString("id");
                    ElasticBoxSlave slave = instanceIdToSlaveMap.get(instanceId);
                    if (Client.InstanceState.DONE.equals(state) && Client.TERMINATE_OPERATIONS.contains(instance.getString("operation"))) {
                        addToTerminatedQueue(slave);
                        iter.remove();
                    } else if (Client.InstanceState.UNAVAILABLE.equals(state)) {
                        Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.INFO, 
                                MessageFormat.format("The instance {0} is unavailable, it will be terminated.", slave.getInstancePageUrl()));
                        slavesToRemove.add(slave);
                        iter.remove();
                    }                      

                    instanceIdToSlaveMap.remove(instanceId);
                }                
            }
            
            // the instances of the remaining slaves no longer exist in ElasticBox, removing them
            slavesToRemove.addAll(instanceIdToSlaveMap.values());
        }
        
        return cloudToInstancesMap;
    }
    
    private Map<ElasticBoxCloud, JSONArray> purgeSlaves(TaskListener listener) throws IOException {
        List<ElasticBoxSlave> slavesToRemove = new ArrayList<ElasticBoxSlave>();
        Map<ElasticBoxCloud, JSONArray> cloudToActiveInstancesMap = collectSlavesToRemove(slavesToRemove);
        
        for (Iterator<ElasticBoxSlave> iter = terminatedSlaves.iterator(); iter.hasNext();) {
            ElasticBoxSlave slave = iter.next();
            try  {
                try {
                    slave.delete();
                } catch (ClientException ex) {
                    if (ex.getStatusCode() == HttpStatus.SC_CONFLICT) {
                        continue;
                    } else if (ex.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                        throw ex;
                    }
                }
                iter.remove();
                removeSlave(slave);
            } catch (IOException ex) {
                log(Level.WARNING, MessageFormat.format("Error deleting ElasticBox slave {0}", slave.getDisplayName()), ex, listener);
            }
        }
        
        for (ElasticBoxSlave badSlave : slavesToRemove) {
            removeSlave(badSlave);
        }
        
        return cloudToActiveInstancesMap;
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
            JSONArray configuredVariables = JSONArray.fromObject(slaveConfig.getVariables());
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
        request.slave.setInstanceStatusMessage(MessageFormat.format("Submitted request to deploy instance {0}", 
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
