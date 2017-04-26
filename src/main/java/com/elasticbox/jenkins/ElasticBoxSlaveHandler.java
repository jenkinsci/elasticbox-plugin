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
import java.util.Arrays;
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

    static class InstanceCreationRequest {
        private ElasticBoxSlave slave;
        private final LaunchSlaveProgressMonitor monitor;

        public static final short MAX_ATTEMPTS = 3;
        private short attempts = 0;

        private InstanceCreationRequest(ElasticBoxSlave slave) {
            this.slave = slave;
            monitor = new LaunchSlaveProgressMonitor(slave);
            attempts++;
        }

        public boolean maxAttemptsReached() {
            return attempts >= MAX_ATTEMPTS;
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

    protected void resubmitRequest(InstanceCreationRequest request) {
        request.attempts++;
        request.monitor.setLaunched();
        ElasticBoxSlave oldSlave = request.slave;
        try {
            if (oldSlave.isSingleUse() ) {
                request.slave =
                        new ElasticBoxSlave( (ProjectSlaveConfiguration) oldSlave.getSlaveConfiguration(), true);
                request.slave.setLabelString(oldSlave.getLabelString() );
            } else {
                request.slave =
                        new ElasticBoxSlave((SlaveConfiguration) oldSlave.getSlaveConfiguration(), oldSlave.getCloud());
            }
            Jenkins.getInstance().addNode(request.slave);
            removeSlave(oldSlave);
            LOGGER.info("Adding new slave attempt to Incoming queue - " + request.slave);
            incomingQueue.add(request);

        } catch (IOException | Descriptor.FormException e) {
            LOGGER.severe("Error creating new slave - " + e.getMessage() );
        }
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
                LOGGER.finest("No pending tasks");
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

        checkNumberOfSlaves();

        SlaveInstanceManager slaveInstanceManager = new SlaveInstanceManager();
        purgeSlaves(slaveInstanceManager, listener);

        boolean saveConfig = processSubmittedQueue(listener);

        saveConfig |= processIncomingQueue(listener, slaveInstanceManager);

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
            final ElasticBoxSlave slave = request.slave;
            try {
                if (!slave.isDeletable() && request.monitor.isDone() ) {
                    if (slave.getComputer() != null && slave.getComputer().isOnline() ) {
                        slave.setInstanceStatusMessage(MessageFormat.format(
                                "Successfully deployed at <a href=\"{0}\">{0}</a>",
                                slave.getInstancePageUrl()));
                        saveNeeded = true;
                        LOGGER.info("Request completed successfully. Removing it from submitted queue - " + slave);
                        iter.remove();
                    } else {
                        if (removeSlaveIfLaunchTimedOut(request, listener)) {
                            LOGGER.info("Request timed out waiting for the computer to be online."
                                    + "Removing slave from Submitted queue - " + slave);
                            iter.remove();
                        }
                    }
                } else if ( !request.monitor.isDone() && removeSlaveIfLaunchTimedOut(request, listener) ) {
                    LOGGER.info("Request timed out. Removing slave from Submitted queue - " + slave);
                    iter.remove();
                }
            } catch (IProgressMonitor.IncompleteException ex) {
                log(Level.SEVERE, ex.getMessage() + ". Attempt=" + request.attempts, ex, listener);

                if (request.maxAttemptsReached() ) {
                    slave.setRemovableFromCloud(false);

                    String cloud = null;
                    try {
                        cloud = slave.getCloud().getDescription();
                    } catch (IOException e) {
                        cloud = "<UNKNOWN>";
                    }
                    AbstractSlaveConfiguration config = slave.getSlaveConfiguration();

                    final String configDescription = (config == null) ? "None" : config.getDescription();
                    log(Level.SEVERE, MessageFormat.format(
                            "Maximum number of attempts reached trying to deploy a new slave for Cloud[{0}] "
                                    + "and Slave Configuration[{1}]",
                            cloud,
                            "".equals(configDescription) ? config.getId() : configDescription));
                } else {
                    if ( slave.isSingleUse() ) {
                        slave.setRemovableFromCloud(false);
                    }
                    resubmitRequest(request);
                    slave.markForTermination();
                    saveNeeded = true;
                }
                iter.remove();

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

        List<ElasticBoxSlave> slavesToRemove = new ArrayList<>();
        for (JSONObject instance : slaveInstanceManager.getInstances()) {
            String state = instance.getString("state");
            String instanceId = instance.getString("id");
            ElasticBoxSlave slave = slaveInstanceManager.getSlave(instanceId);

            if (Client.InstanceState.DONE.equals(state)
                    && Client.TERMINATE_OPERATIONS.contains(instance.getJSONObject("operation").getString("event"))
                    && slave.isRemovableFromCloud() ) {

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
                    slave.getCloud().getClient().forceTerminate(instance.getString("id"));
                } catch (IOException ex) {
                    log(Level.SEVERE,
                            "Error force-terminating the instance of ElasticBox slave - " + slave.getDisplayName(),
                            ex, listener);
                }
                return false;

            default:
                String event = instance.getJSONObject("operation").getString("event");
                if (Client.TERMINATE_OPERATIONS.contains(event) ) {
                    if (slave.isRemovableFromCloud() ) {
                        LOGGER.info("Deleting slave - " + slave);
                        deleteInstance(slave, listener);
                    } else if (!slave.isDeletable() ) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Unavailable Slave has been terminated manually - " + slave);
                        }
                    }
                    return true;
                } else {
                    if (slave.maxDeleteAttemptsReached() ) {
                        return true;
                    }
                    try {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Terminating not required slave - " + slave);
                        }
                        slave.terminate();

                    } catch (IOException e) {
                        log(Level.SEVERE,
                                "Error terminating the instance of ElasticBox slave - " + slave, e, listener);
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
                        removeSlave(slave);
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

    private void deployInstance(InstanceCreationRequest request) throws IOException {
        final ElasticBoxSlave slave = request.slave;
        final ElasticBoxCloud cloud = slave.getCloud();
        final Client ebClient = cloud.getClient();

        LOGGER.info("Deploying box - " + ebClient.getBoxPageUrl(slave.getBoxVersion() ));

        final AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();

        final JSONArray variables = getJenkinsVariables(slave);
        final String workspace = slaveConfig.getWorkspace();

        List<String> tags = new ArrayList<>();
        tags.add(slave.getNodeName() );

        String userTags = slaveConfig.getTags();
        if (StringUtils.isNotEmpty(userTags) ) {
            String[] userTagList = StringUtils.split(userTags, ", ");
            tags.addAll(Arrays.asList(userTagList) );
        }

        IProgressMonitor monitor = ebClient.deploy(slave.getBoxVersion(), slave.getProfileId(), slave.getDisplayName(),
                workspace, tags, variables, null, null, slave.getPolicyVariables(), Constants.AUTOMATIC_UPDATES_OFF);

        slave.setInstanceUrl(monitor.getResourceUrl());
        slave.setInstanceStatusMessage(
                MessageFormat.format("Submitted request to deploy instance <a href=\"{0}\">{0}</a>",
                slave.getInstancePageUrl()));

        request.monitor.setMonitor(monitor);
        request.monitor.setLaunched();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Adding slave to Submitted queue - " + slave);
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
                AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
                if (slaveConfig != null ) {
                    List<ElasticBoxSlave> slaves = slaveConfigToSlaveListMap.get(slaveConfig);
                    if (slaves == null) {
                        slaves = new ArrayList<>();
                        slaveConfigToSlaveListMap.put(slaveConfig, slaves);
                    }
                    slaves.add(slave);
                }
            }
        }
        return slaveConfigToSlaveListMap;
    }

    private void checkNumberOfSlaves(
            ElasticBoxCloud cloud, Map<AbstractSlaveConfiguration, List<ElasticBoxSlave>> slaveConfigToSlaveCountMap)
            throws IOException {

        for (SlaveConfiguration slaveConfig : cloud.getSlaveConfigurations()) {
            List<ElasticBoxSlave> slaveList = slaveConfigToSlaveCountMap.get(slaveConfig);
            int slaveCount = (slaveList == null) ? 0 : slaveList.size();
            if (slaveConfig.getMinInstances() > slaveCount) {

                try {
                    int minInstances = slaveConfig.getMinInstances();
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(MessageFormat.format(
                                "Found less slaves [{0}] than Min limit [{1}] for slave config [{2}] in cloud [{3}]",
                                slaveCount, minInstances, slaveConfig.getDescription(), cloud.getDescription() ));
                    }
                    while (slaveCount < minInstances ) {
                        ElasticBoxSlave slave = new ElasticBoxSlave(slaveConfig, cloud);
                        LOGGER.info(MessageFormat.format(
                                        "New slave [{0}] to be created for slave config [{1}] in cloud [{2}]",
                                        slave, slaveConfig.getDescription(), cloud.getDescription() ));

                        Jenkins.getInstance().addNode(slave);
                        ElasticBoxSlaveHandler.submit(slave);
                        slaveCount++;
                    }
                } catch (IOException | Descriptor.FormException ex) {
                    log(Level.SEVERE, ex.getMessage(), ex);
                }
            } else if (slaveConfig.getMaxInstances() < slaveCount) {
                int maxInstances = slaveConfig.getMaxInstances();
                LOGGER.warning(MessageFormat.format(
                        "Found more slaves [{0}] than Max limit [{1}] for Slave config [{2}] in cloud [{3}]",
                        slaveCount, maxInstances, slaveConfig.getDescription(), cloud.getDescription() ));

                for (ElasticBoxSlave slave: slaveList) {
                    if (slave.getComputer().isIdle() && !slave.isDeletable() ) {
                        slave.markForTermination();
                        slave.setRemovableFromCloud(true);
                        if (--slaveCount <= maxInstances) {
                            break;
                        }
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

    private void checkNumberOfSlaves() throws IOException {
        Map<AbstractSlaveConfiguration, List<ElasticBoxSlave>> slaveCfgToSlaveListMap = countSlavesPerConfiguration();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                checkNumberOfSlaves((ElasticBoxCloud) cloud, slaveCfgToSlaveListMap);
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
            return true;
        }
        return false;
    }

    public static void launchSingleUseSlave(AbstractSlaveConfiguration slaveCfg, String label)
            throws IOException, Descriptor.FormException {

        ElasticBoxSlave slave = new ElasticBoxSlave( (ProjectSlaveConfiguration) slaveCfg, true);
        slave.setLabelString(label);
        Jenkins.getInstance().addNode(slave);
        submit(slave);
    }
}
