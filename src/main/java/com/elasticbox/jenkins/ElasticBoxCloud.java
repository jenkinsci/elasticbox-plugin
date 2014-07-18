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
import com.elasticbox.IProgressMonitor;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.Scrambler;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxCloud extends AbstractCloudImpl {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxCloud.class.getName());
    
    private final String endpointUrl;
    private final int maxInstances;
    private final int retentionTime;
    private final String username;
    private final String password;
    private final List<? extends SlaveConfiguration> slaveConfigurations;
    
    @DataBoundConstructor
    public ElasticBoxCloud(String endpointUrl, int maxInstances, int retentionTime, String username, String password,
            List<? extends SlaveConfiguration> slaveConfigurations) {
        super(username + "@" + endpointUrl, String.valueOf(maxInstances));
        this.endpointUrl = endpointUrl;
        this.maxInstances = maxInstances;
        this.retentionTime = retentionTime;
        this.username = username;
        this.password = Scrambler.scramble(password);
        this.slaveConfigurations = slaveConfigurations;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        JSONArray activeInstances;
        try {
            activeInstances = ElasticBoxSlaveHandler.getActiveInstances();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching active instances", ex);
            return Collections.EMPTY_LIST;
        }
        
        if (activeInstances.size() >= maxInstances) {
            LOGGER.log(Level.WARNING, MessageFormat.format("Cannot provision slave for label \"{0}\" because the maxinum number of ElasticBox instances has been reached.", label.getName()));
            return Collections.EMPTY_LIST;
        }

        // readjust the excess work load by considering the instances that are being deployed or already deployed but not yet connected with Jenkins
        List<ElasticBoxSlave> pendingSlaves = new ArrayList<ElasticBoxSlave>();
        List<ElasticBoxSlave> offlineSlaves = new ArrayList<ElasticBoxSlave>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (label.matches(slave)) {
                    if (ElasticBoxSlaveHandler.isSubmitted(slave)) {
                        pendingSlaves.add(slave);
                    }
                    
                    if (slave.getInstanceUrl() != null && slave.getComputer().isOffline()) {
                        offlineSlaves.add(slave);
                    }
                }
            }
        }
        
        if (!offlineSlaves.isEmpty() && !activeInstances.isEmpty()) {            
            Map<String, JSONObject> idToInstanceMap = new HashMap<String, JSONObject>(activeInstances.size());
            for (Object json : activeInstances) {
                JSONObject instance = (JSONObject) json;
                idToInstanceMap.put(instance.getString("id"), instance);
            }
            
            for (ElasticBoxSlave slave : offlineSlaves) {
                JSONObject instance = idToInstanceMap.get(slave.getInstanceId());
                if (instance != null) {
                    String state = instance.getString("state");
                    String operation = instance.getString("operation");
                    if (Client.ON_OPERATIONS.contains(operation) && (Client.InstanceState.PROCESSING.equals(state) || 
                            Client.InstanceState.DONE.equals(state))) {
                        pendingSlaves.add(slave);
                    }
                }
            }
        }
        
        if (!pendingSlaves.isEmpty()) {
            Map<ElasticBoxSlave, Integer> slaveToNumOfAvailableExecutorsMap = new HashMap<ElasticBoxSlave, Integer>(pendingSlaves.size());
            for (ElasticBoxSlave slave : pendingSlaves) {
                slaveToNumOfAvailableExecutorsMap.put(slave, slave.getNumExecutors());
            }
            
            for (Queue.BuildableItem buildableItem : Queue.getInstance().getBuildableItems()) {
                for (Iterator<ElasticBoxSlave> iter = slaveToNumOfAvailableExecutorsMap.keySet().iterator(); 
                        iter.hasNext() && !slaveToNumOfAvailableExecutorsMap.isEmpty();) {
                    ElasticBoxSlave slave = iter.next();
                    if (slave.canTake(buildableItem) == null) {
                        int numOfAvailabelExecutors = slaveToNumOfAvailableExecutorsMap.get(slave);
                        numOfAvailabelExecutors--;
                        if (numOfAvailabelExecutors == 0) {
                            iter.remove();;
                        }
                        break;
                    }
                }
            };
            
            excessWorkload -= slaveToNumOfAvailableExecutorsMap.size();
        }
         
        List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();        
        while (excessWorkload > 0) {
            try {                
                ElasticBoxSlave newSlave;
                if (isLabelForReusableSlave(label)) {
                    String profileId = label.getName().substring(ElasticBoxLabelFinder.REUSE_PREFIX.length());
                    newSlave = new ElasticBoxSlave(profileId, false, this);
                } else {
                    SlaveConfiguration slaveConfig = findSlaveConfiguration(label, activeInstances);
                    if (slaveConfig == null) {
                        LOGGER.log(Level.WARNING, MessageFormat.format("Cannot provision slave for label \"{0}\" because the maxinum number of ElasticBox instances of all matching slave configurations has been reached.", label.getName()));
                        break;                        
                    }
                    
                    newSlave = new ElasticBoxSlave(slaveConfig, this);
                }
                final ElasticBoxSlave slave = newSlave;
                plannedNodes.add(new NodeProvisioner.PlannedNode(slave.getDisplayName(),
                        new FutureWrapper<Node>(Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                            public Node call() throws Exception {
                                slave.setInUse(true);
                                Jenkins.getInstance().addNode(slave);                                
                                IProgressMonitor monitor = ElasticBoxSlaveHandler.submit(slave);
                                monitor.waitForDone(slave.getLaunchTimeout());
                                if (slave.getComputer() != null && slave.getComputer().isOnline()) {
                                    return slave;
                                } else {
                                    throw new Exception(MessageFormat.format("Cannot deploy slave {0}. See the system log for more details.", slave.getDisplayName()));
                                }                                
                            }
                        })), 1));

                excessWorkload -= slave.getNumExecutors();
            } catch (Descriptor.FormException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                break;
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                break;
            }
        }
        return plannedNodes;
    }

    @Override
    public boolean canProvision(Label label) {
        return isLabelForReusableSlave(label) || getSlaveConfiguration(label) != null;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return Scrambler.descramble(password);
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public int getRetentionTime() {
        return retentionTime;
    }

    public List<? extends SlaveConfiguration> getSlaveConfigurations() {
        return slaveConfigurations != null ? Collections.unmodifiableList(slaveConfigurations) : Collections.EMPTY_LIST;
    }
    
    public Client createClient() throws IOException {
        Client client = new Client(getEndpointUrl(), getUsername(), getPassword());
        client.connect();
        return client;
    }
    
    private boolean isLabelForReusableSlave(Label label) {
        return label != null && label.getName() != null && label.getName().startsWith(ElasticBoxLabelFinder.REUSE_PREFIX);
    }
    
    private SlaveConfiguration getSlaveConfiguration(Label label) {
        if (label == null) {
            return null;
        }
        
        for (SlaveConfiguration slaveConfig : getSlaveConfigurations()) {
            if (label.matches(slaveConfig.getLabelSet())) {
                return slaveConfig;
            }
        }
        
        return null;
    }
    
    private SlaveConfiguration findSlaveConfiguration(Label label, JSONArray activeInstances) {
        Map<String, Integer> environmentToInstanceCountMap = new HashMap<String, Integer>();
        for (Object json : activeInstances) {
            JSONObject instance = (JSONObject) json;
            String environment = instance.getString("environment");
            Integer instanceCount = environmentToInstanceCountMap.get(environment);
            environmentToInstanceCountMap.put(environment, instanceCount == null ? 1 : instanceCount++);
        }
        
        for (SlaveConfiguration slaveConfig : getSlaveConfigurations()) {
            if (label.matches(slaveConfig.getLabelSet())) {
                Integer instanceCount = environmentToInstanceCountMap.get(slaveConfig.getEnvironment());
                if (instanceCount == null || instanceCount < slaveConfig.getMaxInstances()) {
                    return slaveConfig;
                }
            }
        }
        
        return null;
    }
    
    private static class FutureWrapper<V> implements Future<V> {
        private final Future<V> future;
        
        FutureWrapper(Future<V> future) {
            this.future = future;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        public boolean isCancelled() {
            return future.isCancelled();
        }

        public boolean isDone() {
            return future.isDone();
        }

        public V get() throws InterruptedException, ExecutionException {
            try {
                return future.get();
            } catch (CancellationException ex) {
                throw new ExecutionException(ex);
            }
        }

        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return future.get(timeout, unit);
            } catch (CancellationException ex) {
                throw new ExecutionException(ex);
            }
        }        
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "ElasticBox";
        }

        @Override
        public Cloud newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            JSONArray clouds;
            try {
                Object json = req.getSubmittedForm().get("cloud");
                if (json instanceof JSONArray) {
                    clouds = (JSONArray) json;
                } else {
                    clouds = new JSONArray();
                    clouds.add(json);
                }
            } catch (ServletException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
            int ebCloudCount = 0;
            for (Object cloud : clouds) {
                JSONObject json = (JSONObject) cloud;
                if (ElasticBoxCloud.class.getName().equals(json.getString("kind"))) {
                    ebCloudCount++;
                }
                if (ebCloudCount > 1) {
                    throw new FormException("You cannot have more than 2 ElasticBox clouds.", "");
                }
            }
            
            String endpointUrl = formData.getString("endpointUrl");
            try {
                new URL(endpointUrl);
            } catch (MalformedURLException ex) {
                throw new FormException(MessageFormat.format("Invalid End Point URL: {0}", endpointUrl), "endpointUrl");
            }
            
            boolean invalidNumber = false;
            try {
                invalidNumber = formData.getInt("maxInstances") <= 0;
            } catch (JSONException ex) {
                invalidNumber = true;
            } 
            if (invalidNumber) {
                throw new FormException("Invalid Max. No. of Instances, it must be a positive whole number.", "maxInstances");
            }
            
            invalidNumber = false;
            try {
                invalidNumber = formData.getInt("retentionTime") <= 0;
            } catch (JSONException ex) {
                invalidNumber = true;
            } 
            if (invalidNumber) {
                throw new FormException("Invalid Retention Time, it must be a positive whole number.", "retentionTime");
            }
            
            Object slaveConfigurations = formData.get("slaveConfigurations");
            int slaveMaxInstances = 0;
            if (slaveConfigurations instanceof JSONObject) {
                JSONObject config = (JSONObject) slaveConfigurations;
                validateSlaveConfiguration(config);
                slaveMaxInstances += config.getInt("maxInstances");
            } else if (slaveConfigurations instanceof JSONArray) {
                for (Object json : (JSONArray) slaveConfigurations) {
                    JSONObject config = (JSONObject) json;
                    validateSlaveConfiguration(config);
                    slaveMaxInstances += config.getInt("maxInstances");
                }
            }
            
            if (slaveMaxInstances > formData.getInt("maxInstances")) {
                formData.put("maxInstances", slaveMaxInstances);
            }            
            
            return super.newInstance(req, formData);
        }

        public FormValidation doTestConnection(
                @QueryParameter String endpointUrl,
                @QueryParameter String username,
                @QueryParameter String password) {
            Client client = new Client(endpointUrl, username, password);
            try {
                client.connect();
            } catch (IOException ex) {
                return FormValidation.error(ex.getMessage());
            }
            return FormValidation.ok(MessageFormat.format("Connection to {0} was successful.", endpointUrl));
        }

        private void validateSlaveConfiguration(JSONObject slaveConfig) throws FormException {
            if (StringUtils.isBlank(slaveConfig.getString("workspace"))) {
                throw new FormException("No workspace is selected for the slave configuration", "workspace");
            }

            if (StringUtils.isBlank(slaveConfig.getString("box"))) {
                throw new FormException("No Box is selected for the slave configuration", "box");
            }

            if (StringUtils.isBlank(slaveConfig.getString("boxVersion"))) {
                throw new FormException("No Version is selected for the selected box in slave configuration", "boxVersion");
            }

            if (StringUtils.isBlank(slaveConfig.getString("profile"))) {
                throw new FormException("No Deployment Profile is selected for the slave configuration", "profile");
            }

            if (StringUtils.isBlank(slaveConfig.getString("environment"))) {
                throw new FormException("No Environment is specified for the slave configuration", "environment");
            }
            
            if (slaveConfig.getInt("executors") < 1) {
                slaveConfig.put("executors", 1);
            }               
        }
    }
    
    public static final ElasticBoxCloud getInstance() {
        for (Cloud cloud : Hudson.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                return (ElasticBoxCloud) cloud;
            }
        }   
        
        return null;
    }

    public static final ElasticBoxCloud getInstance(String name) {
        for (Cloud cloud : Hudson.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud && cloud.name.equals(name)) {
                return (ElasticBoxCloud) cloud;
            }
        }   
        
        return null;
    }
    
}
