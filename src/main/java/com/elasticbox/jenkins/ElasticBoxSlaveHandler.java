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

import static com.elasticbox.jenkins.ElasticBoxExecutor.threadPool;

import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.Constants;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.util.SlaveInstance;
import com.elasticbox.jenkins.util.VariableResolver;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;

import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;

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

@Extension
public class ElasticBoxSlaveHandler extends ElasticBoxExecutor.Workload {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxSlaveHandler.class.getName());

    public static final int TIMEOUT_MINUTES = Integer.getInteger("elasticbox.jenkins.deploymentTimeout", 60);

    private static final Queue<InstanceCreationRequest> incomingQueue =
            new ConcurrentLinkedQueue<InstanceCreationRequest>();

    private static final Queue<InstanceCreationRequest> submittedQueue =
            new ConcurrentLinkedQueue<InstanceCreationRequest>();

    private static final Queue<ElasticBoxSlave> terminatedSlaves = new ConcurrentLinkedQueue<ElasticBoxSlave>();

    private static class InstanceCreationRequest {
        private final ElasticBoxSlave slave;
        private final LaunchSlaveProgressMonitor monitor;

        public InstanceCreationRequest(ElasticBoxSlave slave) {
            this.slave = slave;
            monitor = new LaunchSlaveProgressMonitor(slave);
        }
    }

    public static final ElasticBoxSlaveHandler getInstance() {
        return Jenkins.getInstance().getExtensionList(
                ElasticBoxExecutor.Workload.class).get(ElasticBoxSlaveHandler.class);
    }

    public static final IProgressMonitor submit(ElasticBoxSlave slave) {
        InstanceCreationRequest newRequest = new InstanceCreationRequest(slave);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Adding new slave to Incoming queue - " + slave);
        }
        incomingQueue.add(newRequest);
        return newRequest.monitor;
    }

    public static final boolean isSubmitted(ElasticBoxSlave slave) {
        for (InstanceCreationRequest request : incomingQueue) {
            if (request.slave == slave) {
                return true;
            }
        }
        return false;
    }

    public static final boolean addToTerminatedQueue(ElasticBoxSlave slave) {
        if (!terminatedSlaves.contains(slave)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Adding slave to Terminated queue. - " + slave);
            }
            terminatedSlaves.add(slave);

            for (Iterator<InstanceCreationRequest> iter = submittedQueue.iterator(); iter.hasNext();) {
                InstanceCreationRequest request = iter.next();
                if (request.slave == slave) {
                    iter.remove();
                }
            }
            return true;
        }
        return false;
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
        if (LOGGER.isLoggable(Level.FINER)) {
            if (terminatedSlaves.isEmpty() && incomingQueue.isEmpty() && submittedQueue.isEmpty() ) {
                LOGGER.finer("No pending tasks");
            } else {
                StringBuilder trace = new StringBuilder(200);
                trace.append("Pending tasks:");
                if ( !terminatedSlaves.isEmpty() ) {
                    trace.append("\n terminatedSlaves - ").append(terminatedSlaves.toString() );
                }
                if ( !incomingQueue.isEmpty() ) {
                    trace.append("\n incomingQueue - ").append(incomingQueue.toString() );
                }
                if ( !submittedQueue.isEmpty() ) {
                    trace.append("\n submittedQueue - ").append(submittedQueue.toString() );
                }
                LOGGER.finer(trace.toString() );
            }
        }

        SlaveInstanceManager slaveInstanceManager = new SlaveInstanceManager();

        purgeSlaves(slaveInstanceManager, listener);

        chechNumberOfSlaves();

        retryPendingRequests();

        boolean saveConfig = processIncomingQueue(listener, slaveInstanceManager);

        saveConfig |= processSubmittedQueue(listener);

        if (saveConfig) {
            try {
                Jenkins.getInstance().save();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Error saving configuration", ex);
            }
        }
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

    private boolean processSubmittedQueue(TaskListener listener) {
        boolean saveNeeded = false;
        for (Iterator<InstanceCreationRequest> iter = submittedQueue.iterator(); iter.hasNext();) {
            InstanceCreationRequest request = iter.next();
            try {
                if (!request.slave.isDeletable() && request.monitor.isDone()) {
                    if (request.slave.getComputer() != null && request.slave.getComputer().isOnline()) {
                        request.slave.setInstanceStatusMessage(MessageFormat.format(
                                "Successfully deployed at <a href=\"{0}\">{0}</a>",
                                request.slave.getInstancePageUrl()));
                        saveNeeded = true;
                        LaunchAttempts.resetAttempts(request.slave.getSlaveConfiguration().getId());
                        LOGGER.info("Request completed successfully. Removing it from submitted queue - "
                                    + request.slave);
                        iter.remove();
                    } else {
                        if (removeSlaveIfLaunchTimedOut(request, listener)) {
                            LOGGER.info("Request timed out. Removing slave from Submitted queue - " + request.slave);
                            iter.remove();
                        }
                    }
                } else {
                    removeSlaveIfLaunchTimedOut(request, listener);
                }
            } catch (IProgressMonitor.IncompleteException ex) {
                AbstractSlaveConfiguration config = request.slave.getSlaveConfiguration();
                log(Level.SEVERE,
                        ex.getMessage() + ". Attempt=" + LaunchAttempts.getAttemptsNumber(config.getId() ),
                        ex,
                        listener);

                if (LaunchAttempts.maxAttemptsReached(config.getId() )) {
                    String cloud = null;
                    try {
                        cloud = request.slave.getCloud().getDescription();
                    } catch (IOException e) {
                        cloud = "<UNKNOWN>";
                    }
                    String cfgDesc = config.getDescription();
                    log(Level.SEVERE, MessageFormat.format(
                            "Maximum number of attempts reached trying to deploy a new slave for Cloud[{0}] "
                                    + "and Slave Configuration[{1}]",
                            cloud,
                            "".equals(cfgDesc) ? config.getId() : cfgDesc));
                } else {
                    LaunchAttempts.attemptFinished(request.slave.getSlaveConfiguration().getId() );
                }
                request.slave.markForTermination();

            } catch (IOException ex) {
                log(Level.SEVERE, ex.getMessage(), ex, listener);
            }
        }
        return saveNeeded;
    }

    private static void removeSlave(ElasticBoxSlave slave) {
        try {
            Jenkins.getInstance().removeNode(slave);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                    MessageFormat.format("Error removing slave {0}", slave.getDisplayName()), ex);
        }
    }

    private static List<ElasticBoxSlave> collectSlavesToRemove(SlaveInstanceManager slaveInstanceManager)
            throws IOException {

        List<ElasticBoxSlave> slavesToRemove = new ArrayList<ElasticBoxSlave>();
        for (JSONObject instance : slaveInstanceManager.getInstances()) {
            String state = instance.getString("state");
            String instanceId = instance.getString("id");
            ElasticBoxSlave slave = slaveInstanceManager.getSlave(instanceId);

            if (Client.InstanceState.DONE.equals(state)
                    && Client.TERMINATE_OPERATIONS.contains(instance.getJSONObject("operation").getString("event"))
                    && !LaunchAttempts.maxAttemptsReached(slave.getSlaveConfiguration().getId() )) {

                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("Found Slave to remove - " + slave);
                }
                addToTerminatedQueue(slave);
            } else if (Client.InstanceState.UNAVAILABLE.equals(state) && !slave.getComputer().isOffline()) {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "The instance {0} is unavailable, it will be terminated.", slave.getInstancePageUrl()));

                slavesToRemove.add(slave);
            }
        }

        slavesToRemove.addAll(slaveInstanceManager.getSlavesWithoutInstance());

        return slavesToRemove;
    }

    private boolean purgeSlave(ElasticBoxSlave slave, TaskListener listener) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Purging slave - " + slave);
        }
        JSONObject instance;
        String state;
        try {
            instance = slave.getInstance();
            state = slave.getInstanceState();
        } catch (IOException ex) {
            if (ex instanceof ClientException && ((ClientException) ex).getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return true;
            }
            log(Level.SEVERE,
                    "Error fetching the instance data of ElasticBox slave - " + slave.getDisplayName(), ex, listener);

            return false;
        }

        switch (state) {
            case Client.InstanceState.PROCESSING:
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("Slave still processing, cannot be purged - " + slave);
                }
                return false;

            case Client.InstanceState.UNAVAILABLE:
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Slave is unavailable - " + slave);
                }
                try {
                    if ( !LaunchAttempts.isLastSlaveAttept(slave) ) {
                        slave.getCloud().getClient().forceTerminate(instance.getString("id"));
                    }
                } catch (IOException ex) {
                    log(Level.SEVERE,
                            "Error force-terminating the instance of ElasticBox slave - " + slave.getDisplayName(),
                            ex, listener);
                }
                return false;

            default:
                String event = instance.getJSONObject("operation").getString("event");
                if (Client.TERMINATE_OPERATIONS.contains(event) ) {
                    if (slave.isSingleUse() ) {
                        LOGGER.info("Deleting finished single use slave - " + slave);
                        deleteInstance(slave, listener);
                    } else {
                        LOGGER.info("Slave is terminated and will be removed from nodes - " + slave);
                        final String configId = slave.getSlaveConfiguration().getId();
                        if (LaunchAttempts.getAttemptsNumber(configId) == 0) {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.fine("Deleting not required slave - " + slave);
                            }
                            deleteInstance(slave, listener);
                        } else if (LaunchAttempts.isLastSlaveAttept(slave) ) {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.fine(
                                        "Last unavailable Slave has been terminated manually. Resetting attempt # - "
                                                + slave);
                            }
                            LaunchAttempts.resetAttempts(configId);
                        }
                    }
                    return true;
                } else {
                    try {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            if (slave.isSingleUse() ) {
                                LOGGER.fine("Terminating finished single use Slave - " + slave);
                            } else {
                                LOGGER.info("Terminating not required slave - " + slave);
                            }
                        }
                        slave.terminate();
                    } catch (IOException e) {
                        log(Level.SEVERE,
                                "Error terminating the instance of ElasticBox slave - " + slave.getDisplayName(),
                                e, listener);
                    }
                }
                return false;
        }
    }

    private boolean deleteInstance(ElasticBoxSlave slave, TaskListener listener) {
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
            log(Level.SEVERE, "Error deleting ElasticBox slave - " + slave.getDisplayName(), ex, listener);
            return false;
        }
    }

    private void purgeSlaves(SlaveInstanceManager slaveInstanceManager, final TaskListener listener)
            throws IOException {

        // terminate slaves that are marked as deletable
        Collection<ElasticBoxSlave> slaves = slaveInstanceManager.getSlaves();
        for (ElasticBoxSlave slave : slaves) {
            if (slave.isDeletable() && slaveInstanceManager.getInstance(slave) != null) {
                if (addToTerminatedQueue(slave)) {
                    LOGGER.info("Deletable slave instance added to terminated queue - " + slave);
                }
            }
        }

        // remove terminated slaves
        for (final ElasticBoxSlave slave: terminatedSlaves) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Terminated slave instance found. - " + slave);
            }
            threadPool.submit(new Runnable() {

                @Override
                public void run() {
                    if (purgeSlave(slave, listener)) {
                        terminatedSlaves.remove(slave);
                        if ( !LaunchAttempts.isLastSlaveAttept(slave) ) {
                            removeSlave(slave);
                        }
                    }
                }

            });
        }

        // remove bad slaves
        List<ElasticBoxSlave> slavesToRemove = collectSlavesToRemove(slaveInstanceManager);
        for (final ElasticBoxSlave slave : slavesToRemove) {
            if (!isSubmitted(slave) ) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Removable slave instance found - " + slave);
                }
                threadPool.submit(new Runnable() {

                    @Override
                    public void run() {
                        removeSlave(slave);
                    }

                });
            }
        }
    }

    private void retryPendingRequests() {
        List<ElasticBoxSlave> pendingRequests = LaunchAttempts.getPendingSlaves();
        for (ElasticBoxSlave slave: pendingRequests) {
            if (slave.isSingleUse() ) {
                try {
                    String event = slave.getInstance().getJSONObject("operation").getString("event");
                    if (Client.InstanceState.UNAVAILABLE.equals(slave.getInstanceState() )) {

                        LOGGER.info("Retrying to deploy single use slave - " + slave);
                        launchSingleUseSlave(slave.getSlaveConfiguration(), slave.getLabelString() );
                    }
                } catch (IOException | Descriptor.FormException e) {
                    if (e instanceof ClientException && ((ClientException) e).getStatusCode()
                            == HttpStatus.SC_NOT_FOUND) {
                        if (LOGGER.isLoggable(Level.FINER)) {
                            LOGGER.finer("Slave doesn't have a deployed instance - " + slave);
                        }
                    } else {
                        LOGGER.severe("Error submitting new attept to deploy single use slave - "
                                + slave + " - " + e.getMessage() + " - Label: " + slave.getLabelString());
                    }
                }
            }
        }
    }

    private void deployInstance(InstanceCreationRequest request) throws IOException {
        ElasticBoxCloud cloud = request.slave.getCloud();
        Client ebClient = cloud.getClient();
        AbstractSlaveConfiguration slaveConfig = request.slave.getSlaveConfiguration();
        String workspace = slaveConfig.getWorkspace();
        JSONArray variables = getJenkinsVariables(request.slave);

        LOGGER.info("Deploying box - " + ebClient.getBoxPageUrl(request.slave.getBoxVersion() ));

        IProgressMonitor monitor = ebClient.deploy(request.slave.getBoxVersion(), request.slave.getProfileId(), null,
                workspace, Collections.singletonList(request.slave.getNodeName()), variables, null,
                null, request.slave.getPolicyVariables(), Constants.AUTOMATIC_UPDATES_OFF);

        request.slave.setInstanceUrl(monitor.getResourceUrl());
        request.slave.setInstanceStatusMessage(
                MessageFormat.format("Submitted request to deploy instance <a href=\"{0}\">{0}</a>",
                request.slave.getInstancePageUrl()));

        request.monitor.setMonitor(monitor);
        request.monitor.setLaunched();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Adding slave to Submitted queue - " + request.slave);
        }
        submittedQueue.add(request);
    }

    private JSONArray getJenkinsVariables(ElasticBoxSlave slave) throws IOException {
        Client ebClient = slave.getCloud().getClient();
        AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
        JSONArray variables = SlaveInstance.createJenkinsVariables(ebClient, slave);
        JSONObject jenkinsVariable = variables.getJSONObject(0);

        String scope = jenkinsVariable.containsKey("scope") ? jenkinsVariable.getString("scope") : StringUtils.EMPTY;

        if (slaveConfig != null && slaveConfig.getVariables() != null) {
            JSONArray configuredVariables = VariableResolver.parseVariables(slaveConfig.getVariables() );
            for (int i = 0; i < configuredVariables.size(); i++) {
                JSONObject variable = configuredVariables.getJSONObject(i);
                if (!scope.equals(variable.getString("scope"))
                        || !SlaveInstance.REQUIRED_VARIABLES.contains(variable.getString("name") )) {
                    variables.add(variable);
                }
            }
        }
        return variables;
    }

    private Map<AbstractSlaveConfiguration, List<ElasticBoxSlave>> countSlavesPerConfiguration() {
        Map<AbstractSlaveConfiguration, List<ElasticBoxSlave>> slaveConfigToSlaveListMap = new HashMap<>();

        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getInstanceUrl() != null) {
                    AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
                    try {
                        if (slaveConfig != null && !slave.isTerminated() && !slave.isDeletable() ) {
                            List<ElasticBoxSlave> slaves = slaveConfigToSlaveListMap.get(slaveConfig);
                            if (slaves == null) {
                                slaves = new ArrayList<>();
                                slaveConfigToSlaveListMap.put(slaveConfig, slaves);
                            }
                            slaves.add(slave);
                        }
                    } catch (IOException e) {
                        LOGGER.severe("Error fetching info from slave - " + slave);
                    }
                }
            }
        }
        return slaveConfigToSlaveListMap;
    }

    private void chechNumberOfSlaves(
            ElasticBoxCloud cloud, Map<AbstractSlaveConfiguration, List<ElasticBoxSlave>> slaveConfigToSlaveCountMap)
            throws IOException {

        for (SlaveConfiguration slaveConfig : cloud.getSlaveConfigurations()) {
            if (slaveConfig.getMinInstances() > 0) {
                List<ElasticBoxSlave> slaveList = slaveConfigToSlaveCountMap.get(slaveConfig);
                int slaveCount = (slaveList == null) ? 0 : slaveList.size();
                if ((slaveConfig.getMinInstances() > slaveCount)
                        && !LaunchAttempts.maxAttemptsReached(slaveConfig.getId() )) {

                    try {
                        ElasticBoxSlave slave = new ElasticBoxSlave(slaveConfig, cloud);
                        LOGGER.info("New slave to be created - " + slave);
                        Jenkins.getInstance().addNode(slave);
                        ElasticBoxSlaveHandler.submit(slave);
                        LaunchAttempts.addAttempt(slaveConfig.getId(), slave );
                        break;
                    } catch (IOException | Descriptor.FormException ex) {
                        log(Level.SEVERE, ex.getMessage(), ex);
                    }
                } else if (slaveConfig.getMaxInstances() < slaveCount) {
                    LaunchAttempts.resetAttempts(slaveConfig.getId() );
                    int maxInstances = slaveConfig.getMaxInstances();
                    LOGGER.warning(MessageFormat.format(
                            "Found more slaves [{0}] than Max limit [{1}]. THey sould be terminated",
                            slaveCount, maxInstances));

                    int index = 0;
                    while (index < slaveList.size() ) {
                        ElasticBoxSlave slave = slaveList.get(index);
                        if (slave.getComputer().isIdle() ) {
                            slave.markForTermination();
                            if (--slaveCount <= maxInstances) {
                                break;
                            }
                            slaveList.remove(slave);
                        } else {
                            index++;
                        }
                    }
                    if (maxInstances < slaveCount) {
                        for (ElasticBoxSlave slave: slaveList) {
                            slave.markForTermination();
                            if (--slaveCount <= maxInstances) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void chechNumberOfSlaves() throws IOException {
        Map<AbstractSlaveConfiguration, List<ElasticBoxSlave>> slaveCfgToSlaveListMap = countSlavesPerConfiguration();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                chechNumberOfSlaves((ElasticBoxCloud) cloud, slaveCfgToSlaveListMap);
            }
        }
    }

    private boolean processIncomingQueue(TaskListener listener, SlaveInstanceManager instanceManager )
            throws IOException {

        if (!incomingQueue.isEmpty() ) {
            Map<ElasticBoxCloud, Integer> cloudToMaxNewInstancesMap = instanceManager.getMaxInstancesPerCloud();

            for (InstanceCreationRequest req = incomingQueue.poll(); req != null; req = incomingQueue.poll()) {
                ElasticBoxCloud cloud = req.slave.getCloud();
                int maxNewInstances = cloudToMaxNewInstancesMap.get(cloud);
                if (maxNewInstances > 0) {
                    try {
                        deployInstance(req);
                        cloudToMaxNewInstancesMap.put(cloud, maxNewInstances--);
                        log("Deploying a new instance for slave - " + req.slave.getDisplayName(), listener);
                        return true;
                    } catch (IOException ex) {
                        log(Level.SEVERE, MessageFormat.format("Error deploying a new instance for slave {0}",
                                req.slave.getDisplayName()), ex, listener);
                        req.monitor.setMonitor(IProgressMonitor.DONE_MONITOR);
                        removeSlave(req.slave);
                    }
                } else {
                    log(Level.WARNING, "Max number of ElasticBox instances has been reached for: "
                            + cloud.getDisplayName(), null, listener);

                    req.monitor.setMonitor(IProgressMonitor.DONE_MONITOR);
                    removeSlave(req.slave);
                }
            }
        }
        return false;
    }

    public static void launchSingleUseSlave(AbstractSlaveConfiguration slaveCfg, String label)
            throws IOException, Descriptor.FormException {

        ElasticBoxSlave slave = new ElasticBoxSlave( (ProjectSlaveConfiguration) slaveCfg, true);
        slave.setLabelString(label);
        Jenkins.getInstance().addNode(slave);
        submit(slave);
        LaunchAttempts.addAttempt(slaveCfg.getId(), slave);
    }
}
