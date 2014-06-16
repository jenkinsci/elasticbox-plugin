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
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Node;
import hudson.model.TaskListener;
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
                            return slave.getComputer().isOffline();
                        }
                    }, remainingTime);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
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
    
    public static int countInstances() throws IOException {
        return collectSlavesToRemove(new ArrayList<ElasticBoxSlave>());
    }
        
    public ElasticBoxSlaveHandler() {
        super("ElasticBox Slave Handler");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        if (ElasticBoxCloud.getInstance() == null) {
            return;
        }
        
        int numOfRemainingSlaves = purgeSlaves(listener);        
        
        ElasticBoxCloud cloud = ElasticBoxCloud.getInstance();
        if (cloud != null) {
            int numOfAllowedInstances = cloud.getMaxInstances() - numOfRemainingSlaves;
            if (numOfAllowedInstances > 0) {
                boolean saveConfig = false;
                for (int i = 0; i < numOfAllowedInstances; i++) {
                    InstanceCreationRequest request = incomingQueue.poll();
                    if (request == null) {
                        break;
                    }

                    try {
                        deployInstance(request);
                        saveConfig = true;
                        log(MessageFormat.format("Deloying a new instance for slave {0}", request.slave.getDisplayName()), listener);                    
                    } catch (IOException ex) {
                        log(Level.SEVERE, MessageFormat.format("Error deloying a new instance for slave {0}", request.slave.getDisplayName()), ex, listener);
                        request.monitor.setMonitor(DONE_MONITOR);
                        removeSlave(request.slave);
                    }
                }
                if (saveConfig) {
                    Jenkins.getInstance().save();
                }
            } else {
                log(Level.WARNING, "Max number of ElasticBox instances has been reached", null, listener);
            }            
        }
        
        processSubmittedQueue(listener);
        
    }

    @Override
    public long getRecurrencePeriod() {
        return 10000;
    }
    
    private void processSubmittedQueue(TaskListener listener) {
        boolean saveNeeded = false;
        for (Iterator<InstanceCreationRequest> iter = submittedQueue.iterator(); iter.hasNext();) {
            InstanceCreationRequest request = iter.next();
            try {
                if (request.monitor.isDone()) {
                    if (request.slave.getComputer() != null && request.slave.getComputer().isOnline()) {
                        request.slave.setInstanceStatusMessage(MessageFormat.format("Successfully deployed at {0}", request.slave.getInstancePageUrl()));
                        saveNeeded = true;
                        iter.remove();
                    } else if ((System.currentTimeMillis() - request.monitor.getCreationTime()) >= TIMEOUT) {
                        request.slave.setInUse(false);
                        iter.remove();
                        log(Level.SEVERE, MessageFormat.format("Slave agent {0} didn't contact after {1} minutes.", request.slave.getNodeName(), TIMEOUT_MINUTES), null, listener);
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
            Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.SEVERE, MessageFormat.format("Error removing slave {0}", slave.getDisplayName()), ex);
        }        
    }
    
    private static int collectSlavesToRemove(List<ElasticBoxSlave> slavesToRemove) throws IOException {
        ElasticBoxCloud cloud = ElasticBoxCloud.getInstance();
        Client ebClient = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        int numOfInstances = 0;
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave && !isSlaveInQueue((ElasticBoxSlave) node, incomingQueue)) {
                final ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.isSingleUse() && slave.canTerminate() && slave.getComputer() != null) {
                    boolean remove = true;
                    for (hudson.model.Queue.BuildableItem item : Jenkins.getInstance().getQueue().getBuildableItems(slave.getComputer())) {
                        if (!item.getFuture().isCancelled()) {
                            remove = false;
                            break;
                        };
                    }
                    if (remove) {
                        slavesToRemove.add(slave);
                        break;
                    }
                }
                if (slave.getInstanceUrl() != null) {
                    try {
                        JSONObject instance = ebClient.getInstance(slave.getInstanceId());
                        String state = instance.getString("state");
                        if (Client.InstanceState.DONE.equals(state) && Client.TERMINATE_OPERATIONS.contains(instance.getString("operation"))) {
                            addToTerminatedQueue(slave);
                        } else if (Client.InstanceState.UNAVAILABLE.equals(state) || (slave.canTerminate() && !isSlaveInQueue(slave, submittedQueue)) ) {
                            Logger.getLogger(ElasticBoxSlaveHandler.class.getName()).log(Level.INFO, MessageFormat.format("Unavailable instance {0} will be terminated.", slave.getInstancePageUrl()));
                            slavesToRemove.add(slave);
                        } else {
                            numOfInstances++;
                        }                      
                    } catch (ClientException ex) {
                        if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                            slavesToRemove.add(slave);
                        }
                    }
                } else if (!slave.isInUse()) {
                    slavesToRemove.add(slave);
                }
            }
        }       
        return numOfInstances;
    }
    
    private int purgeSlaves(TaskListener listener) throws IOException {
        List<ElasticBoxSlave> slavesToRemove = new ArrayList<ElasticBoxSlave>();
        int numOfInstances = collectSlavesToRemove(slavesToRemove);
        
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
        
        return numOfInstances;
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
        String environment = request.slave.getNodeName();
        if (environment.length() > 30) {
            environment = environment.substring(0, 30);
        }
        
        Map<String, String> variables = new HashMap<String, String>();
        variables.put("JENKINS_URL", Jenkins.getInstance().getRootUrl());
        variables.put("SLAVE_NAME", request.slave.getNodeName());
        JSONObject profile = ebClient.getProfile(request.slave.getProfileId());
        IProgressMonitor monitor = ebClient.deploy(request.slave.getProfileId(), profile.getString("owner"), environment, 1, variables);
        request.slave.setInstanceUrl(monitor.getResourceUrl());
        request.slave.setInstanceStatusMessage(MessageFormat.format("Submitted request to deploy instance {0}", request.slave.getInstancePageUrl()));
        request.monitor.setMonitor(monitor);
        submittedQueue.add(request);
    }

}
