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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class ElasticBoxSlaveHandler extends AsyncPeriodicWork {
    public static final int TIMEOUT_MINUTES = 60;
    private static final long TIMEOUT = TIMEOUT_MINUTES * 60000;

    public static class InstanceCreationRequest {
        private final ElasticBoxSlave slave;
        private IProgressMonitor monitor;

        public InstanceCreationRequest(ElasticBoxSlave slave) {
            this.slave = slave;
        }
        
    }
    private static final Queue<InstanceCreationRequest> incomingQueue = new ConcurrentLinkedQueue<InstanceCreationRequest>();
    private static final Queue<InstanceCreationRequest> submittedQueue = new ConcurrentLinkedQueue<InstanceCreationRequest>();
    private static final Queue<ElasticBoxSlave> terminatedSlaves = new ConcurrentLinkedQueue<ElasticBoxSlave>();
    
    public static final void submit(ElasticBoxSlave slave) {
        incomingQueue.add(new InstanceCreationRequest(slave));
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
                for (int i = 0; i < numOfAllowedInstances; i++) {
                    InstanceCreationRequest request = incomingQueue.poll();
                    if (request == null) {
                        break;
                    }

                    try {
                        deployInstance(request);
                        log(MessageFormat.format("Deloying a new instance for slave {0}", request.slave.getDisplayName()), listener);                    
                    } catch (IOException ex) {
                        log(Level.SEVERE, MessageFormat.format("Error deloying a new instance for slave {0}", request.slave.getDisplayName()), ex, listener);
                        request.slave.setInstanceStatusMessage(MessageFormat.format("Instance cannot be deployed. Error: {0}", 
                                ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 1024))));
                    }
                }
                Jenkins.getInstance().save();
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
                    if (request.slave.getComputer().isOnline()) {
                        request.slave.setInstanceStatusMessage(MessageFormat.format("Successfully deployed at {0}", request.slave.getInstanceUrl()));
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
                if (slave.isSingleUse() && slave.getComputer() != null) {
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
                slave.delete();
                iter.remove();
                removeSlave(slave);
            } catch (ClientException ex) {
                if (ex.getStatusCode() != HttpStatus.SC_CONFLICT) {
                    log(Level.WARNING, MessageFormat.format("Error deleting ElasticBox slave {0}", slave.getDisplayName()), ex, listener);
                }
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
        Logger.getLogger(InstanceCreator.class.getName()).log(level, message, exception);       
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
        request.slave.setInstanceStatusMessage(MessageFormat.format("Submitted request to deploy instance {0}", request.slave.getInstanceUrl()));
        request.monitor = monitor;
        submittedQueue.add(request);
    }

}
