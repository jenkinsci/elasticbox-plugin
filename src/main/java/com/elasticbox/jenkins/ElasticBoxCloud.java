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

import antlr.ANTLRException;
import com.elasticbox.Client;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.SlaveInstance;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    public static final String ENDPOINT_URL = "endpointUrl";
    public static final String USER_NAME = "username";
    public static final String PASSWORD = "password";
    
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxCloud.class.getName());
    private static final String NAME_PREFIX = "elasticbox-";
    
    private final String endpointUrl;
    private int maxInstances;
    @Deprecated
    private int retentionTime;
    @Deprecated
    private final String username;
    @Deprecated
    private final String password;
    private final String token;
    private final List<? extends SlaveConfiguration> slaveConfigurations;
    private String description;
    
    @DataBoundConstructor
    public ElasticBoxCloud(String name, String description, String endpointUrl, int maxInstances, String token,
            List<? extends SlaveConfiguration> slaveConfigurations) {
        super(name, String.valueOf(maxInstances));
        this.description = description;
        this.endpointUrl = endpointUrl;
        this.maxInstances = maxInstances;
        this.token = token;
        this.slaveConfigurations = slaveConfigurations;
        username = password = null;
        retentionTime = 0;
    }
    
    protected Object readResolve() {
        if (StringUtils.isBlank(description) && StringUtils.isNotBlank(username)) {
            description = getDisplayName();
        }
        return this;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String getDisplayName() {
        if (StringUtils.isBlank(description)) {
            return username + '@' + endpointUrl;
        } else {
            return description;
        }
    }

    public String getToken() {
        return token;
    }
    
    private List<ElasticBoxSlave> getPendingSlaves(Label label, List<JSONObject> activeInstances) {
        List<ElasticBoxSlave> pendingSlaves = new ArrayList<ElasticBoxSlave>();
        List<ElasticBoxSlave> offlineSlaves = new ArrayList<ElasticBoxSlave>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                ElasticBoxCloud slaveCloud = null;
                try {
                    slaveCloud = slave.getCloud();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
                if (slaveCloud == this && label.matches(slave)) {
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
            for (JSONObject instance : activeInstances) {
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
        
        return pendingSlaves;
    }
            
    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {
            return doProvision(label, excessWorkload);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            return Collections.EMPTY_LIST;
        }
    }
    
    private Collection<NodeProvisioner.PlannedNode> doProvision(Label label, int excessWorkload) {    
        List<JSONObject> activeInstances;
        try {
            activeInstances = ElasticBoxSlaveHandler.getActiveInstances(this);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching active instances", ex);
            return Collections.EMPTY_LIST;
        }
        
        if (activeInstances.size() >= maxInstances) {
            LOGGER.log(Level.WARNING, 
                    MessageFormat.format("Cannot provision slave for label ''{0}'' because the maxinum number of instances has been reached for ElasticBox cloud {1}.", 
                            label.getName(), getDisplayName()));
            return Collections.EMPTY_LIST;
        }

        // readjust the excess work load by considering the instances that are being deployed or already deployed but not yet connected with Jenkins
        List<ElasticBoxSlave> pendingSlaves = getPendingSlaves(label, activeInstances);
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
            
            for (int numOfAvailableExecutors : slaveToNumOfAvailableExecutorsMap.values()) {
                excessWorkload -= numOfAvailableExecutors;                
            }
        }
        
        if (excessWorkload <= 0) {
            LOGGER.log(Level.INFO, MessageFormat.format("Skipped provisioning slave for label ''{0}'' because there are enough slaves are being launched in ElasticBox cloud {1}.", label.getName(), getDisplayName()));
        }
         
        List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();        
        while (excessWorkload > 0) {
            try {                
                ElasticBoxSlave newSlave;
                if (isLabelForReusableSlave(label)) {
                    ProjectSlaveConfiguration slaveConfig = ProjectSlaveConfiguration.find(label);
                    if (slaveConfig != null) {
                        SlaveInstance.InstanceCounter instanceCounter = new SlaveInstance.InstanceCounter(activeInstances);
                        if (instanceCounter.count(slaveConfig) >= slaveConfig.getMaxInstances()) {
                            LOGGER.log(Level.WARNING, MessageFormat.format("Cannot provision slave for label \"{0}\" because the maxinum number of ElasticBox instances of the slave configuration has been reached.", label.getName()));
                            break;                        
                        }
                        newSlave = new ElasticBoxSlave(slaveConfig, false);
                    } else {
                        LOGGER.log(Level.WARNING, MessageFormat.format("Cannot find any slave configuration for label ''{0}''. No slave will be provisioned.", label.getName()));
                        break;
                    }
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
                                Jenkins.getInstance().addNode(slave);                                
                                IProgressMonitor monitor = ElasticBoxSlaveHandler.submit(slave);
                                monitor.waitForDone(slave.getLaunchTimeout());
                                if (slave.getComputer() != null && slave.getComputer().isOnline()) {
                                    return slave;
                                } else {
                                    LOGGER.log(Level.WARNING, MessageFormat.format("The slave {0} did not come online after {1} minutes. It will be terminated and removed.", slave.getDisplayName(), slave.getLaunchTimeout()));
                                    ElasticBoxSlaveHandler.markForTermination(slave);
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
        try {
            if (isLabelForReusableSlave(label)) {
                ProjectSlaveConfiguration slaveConfig = ProjectSlaveConfiguration.find(label);
                return slaveConfig != null && slaveConfig.getCloud().equals(name);
            }

            return getSlaveConfiguration(label) != null;
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        }
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    @Deprecated
    public String getUsername() {
        return username;
    }

    @Deprecated
    public String getPassword() {
        return Scrambler.descramble(password);
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    @Deprecated
    public int getRetentionTime() {
        return retentionTime;
    }

    public List<? extends SlaveConfiguration> getSlaveConfigurations() {
        return slaveConfigurations != null ? Collections.unmodifiableList(slaveConfigurations) : Collections.EMPTY_LIST;
    }
    
    public Client getClient() throws IOException {
        return ClientCache.findOrCreateClient(name);
    }
    
    SlaveConfiguration getSlaveConfiguration(String configId) {
        for (SlaveConfiguration config : getSlaveConfigurations()) {
            if (configId.equals(config.getId())) {
                return config;
            }
        }
        
        return null;
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
    
    private SlaveConfiguration findSlaveConfiguration(Label label, List<JSONObject> activeInstances) {
        SlaveInstance.InstanceCounter instanceCounter = new SlaveInstance.InstanceCounter(activeInstances);
        
        for (SlaveConfiguration slaveConfig : getSlaveConfigurations()) {
            if (label.matches(slaveConfig.getLabelSet()) && 
                    instanceCounter.count(slaveConfig) < slaveConfig.getMaxInstances()) {
                return slaveConfig;
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
            ElasticBoxCloud newCloud = (ElasticBoxCloud) super.newInstance(req, formData);
            
            try {
                new URL(newCloud.endpointUrl);
            } catch (MalformedURLException ex) {
                throw new FormException(MessageFormat.format("Invalid End Point URL: {0}", newCloud.endpointUrl), ENDPOINT_URL);
            }

            if (StringUtils.isBlank(newCloud.description)) {
                throw new FormException(MessageFormat.format("Description is required for ElasticBox cloud at {0}", newCloud.getEndpointUrl()), "description");
            }       
            
            boolean invalidNumber;
            try {
                invalidNumber = newCloud.maxInstances <= 0;
            } catch (JSONException ex) {
                invalidNumber = true;
            } 
            if (invalidNumber) {
                throw new FormException(MessageFormat.format("Invalid Max. No. of Instances for ElasticBox cloud {0}, it must be a positive whole number.", newCloud.getDisplayName()), "maxInstances");
            }
            
            if (StringUtils.isBlank(newCloud.token)) {
                throw new FormException(MessageFormat.format("Authentication token is required for ElasticBox cloud {0}", newCloud.getDisplayName()), "token");
            }       
            
            checkDeletedSlaveConfiguration(newCloud);
            
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
            List<ElasticBoxCloud> cloudsToRemoveCachedClient = validateClouds(clouds);

            int slaveMaxInstances = 0;
            for (SlaveConfiguration config : newCloud.getSlaveConfigurations()) {
                validateSlaveConfiguration(config, newCloud);
                slaveMaxInstances += config.getMaxInstances();
            }
            
            if (slaveMaxInstances > newCloud.maxInstances) {
                newCloud.maxInstances = slaveMaxInstances;
            }          
            
            if (StringUtils.isBlank(newCloud.name)) {
                newCloud = new ElasticBoxCloud(NAME_PREFIX + UUID.randomUUID().toString(), newCloud.getDescription(), newCloud.getEndpointUrl(), 
                        newCloud.getMaxInstances(), newCloud.getToken(), newCloud.getSlaveConfigurations());
            }
            
            for (ElasticBoxCloud cloud : cloudsToRemoveCachedClient) {
                ClientCache.removeClient(cloud);
            }
            
            return newCloud;
        }

        public FormValidation doGetToken(
                @QueryParameter String endpointUrl,
                @QueryParameter String username,
                @QueryParameter String password) {
            String token = null;
            try {
                token = DescriptorHelper.getToken(endpointUrl, username, password);
            } catch (IOException ex) {
                return FormValidation.error(ex.getMessage());
            }
            return FormValidation.ok(token);
        }
        
        public FormValidation doVerifyToken(@QueryParameter String endpointUrl, @QueryParameter String token) {
            Client client = new Client(endpointUrl, token);
            try {
                client.connect();
            } catch (IOException ex) {
                return FormValidation.error(ex.getMessage());
            }
            return FormValidation.ok(MessageFormat.format("The authentication token is valid for {0}.", endpointUrl));
        }
        
        private List<ElasticBoxCloud> validateClouds(JSONArray clouds) throws FormException {
            // check for unique description
            Set<String> takenDescriptions = new HashSet<String>();
            Map<String, JSONObject> nameToExistingCloudMap = new HashMap<String, JSONObject>();
            for (Object cloud : clouds) {
                JSONObject json = (JSONObject) cloud;
                if (ElasticBoxCloud.class.getName().equals(json.getString("kind"))) {
                    String description = json.getString("description");
                    if (takenDescriptions.contains(description)) {
                        throw new FormException(MessageFormat.format("There are more than one ElasticBox clouds with description ''{0}''. Please specify unique description.", description), null);
                    } else {
                        takenDescriptions.add(description);
                    }
                    
                    String name = json.getString("name");
                    if (!StringUtils.isBlank(name)) {
                        nameToExistingCloudMap.put(name, json);
                    }                                        
                }
            }
            Set<ElasticBoxCloud> cloudsWithSlaves = new HashSet<ElasticBoxCloud>();
            for (Node node : Jenkins.getInstance().getNodes()) {
                if (node instanceof ElasticBoxSlave) {
                    try {
                        cloudsWithSlaves.add(((ElasticBoxSlave) node).getCloud());
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }
            
            Set<ElasticBoxCloud> deletedClouds = new HashSet<ElasticBoxCloud>();
            List<ElasticBoxCloud> cloudsToRemoveCachedClient = new ArrayList<ElasticBoxCloud>();
            for (Cloud cloud : Jenkins.getInstance().clouds) {
                if (cloud instanceof ElasticBoxCloud) {
                    ElasticBoxCloud ebCloud = (ElasticBoxCloud) cloud;
                    JSONObject json = nameToExistingCloudMap.get(ebCloud.name);                    
                    if (json == null) {
                        deletedClouds.add(ebCloud);
                        cloudsToRemoveCachedClient.add(ebCloud);
                    } else {
                        if (!ebCloud.getEndpointUrl().equalsIgnoreCase(json.getString(ENDPOINT_URL)) || 
                                !json.getString("token").equals(ebCloud.getToken())) {
                            cloudsToRemoveCachedClient.add(ebCloud);
                        }
                    }                    
                }
            }
            cloudsWithSlaves.retainAll(deletedClouds);
            if (!cloudsWithSlaves.isEmpty()) {
                List<String> names = new ArrayList<String>();
                for (ElasticBoxCloud cloud : cloudsWithSlaves) {
                    names.add(cloud.getDisplayName());
                }
                throw new FormException(MessageFormat.format("The following ElasticBox clouds cannot be deleted because they still have one or many slaves: {0}. Please delete the slaves and try again.", 
                        StringUtils.join(names, ", ")), null);
            }
            
            return cloudsToRemoveCachedClient;
        }
        
        private void validateSlaveConfiguration(SlaveConfiguration slaveConfig, ElasticBoxCloud newCloud) throws FormException {
            if (StringUtils.isBlank(slaveConfig.getWorkspace())) {
                throw new FormException(MessageFormat.format("No workspace is selected for a slave configuration of ElasticBox cloud {0}.", newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (StringUtils.isBlank(slaveConfig.getBox())) {
                throw new FormException(MessageFormat.format("No Box is selected for a slave configurationof ElasticBox cloud {0}.", newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (StringUtils.isBlank(slaveConfig.getBoxVersion())) {
                throw new FormException(MessageFormat.format("No Version is selected for the selected box in a slave configurationof ElasticBox cloud {0}.", newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (StringUtils.isBlank(slaveConfig.getProfile())) {
                throw new FormException(MessageFormat.format("No Deployment Profile is selected for a slave configurationof ElasticBox cloud {0}.", newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            String environment = slaveConfig.getEnvironment();
            if (StringUtils.isBlank(environment)) {
                throw new FormException(MessageFormat.format("No Environment is specified for a slave configuration of ElasticBox cloud {0}.", newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }
            
            environment = environment.trim();
            if (environment.length() > 30) {
                throw new FormException(MessageFormat.format("Environment of a slave configuration of ElasticBox cloud {0} is longer than 30 characters.", newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }
            
            slaveConfig.setEnvironment(environment);
            for (SlaveConfiguration config : newCloud.getSlaveConfigurations()) {
                if (config != slaveConfig && config.getEnvironment() != null && environment.equals(config.getEnvironment().trim())) {
                    throw new FormException("Duplicate Environment specified for slave configurations", SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
            }
            
            if (slaveConfig.getExecutors() < 1) {
                slaveConfig.setExecutors(1);
            }  
            
            if (StringUtils.isBlank(slaveConfig.getId())) {
                slaveConfig.setId(UUID.randomUUID().toString());
            }
            
            FormValidation result = ((SlaveConfiguration.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(SlaveConfiguration.class)).doCheckBoxVersion(slaveConfig.getBoxVersion(), 
                    newCloud.getEndpointUrl(), newCloud.getUsername(), newCloud.getPassword(), newCloud.getToken(), slaveConfig.getBox());
            if (result.kind == FormValidation.Kind.ERROR) {
                throw new FormException(result.getMessage(), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }
        }

        private void checkDeletedSlaveConfiguration(ElasticBoxCloud newCloud) throws FormException {
            Cloud cloud = Jenkins.getInstance().getCloud(newCloud.name);
            if (!(cloud instanceof ElasticBoxCloud)) {
                return;
            }
            
            ElasticBoxCloud existingCloud = (ElasticBoxCloud) cloud;
            for (Node node : Jenkins.getInstance().getNodes()) {
                if (node instanceof ElasticBoxSlave) {
                    ElasticBoxSlave slave = (ElasticBoxSlave) node;
                    try {
                        cloud = slave.getCloud();
                    } catch (IOException ex) {
                        Logger.getLogger(ElasticBoxCloud.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                    }
                    if (cloud == existingCloud && StringUtils.isNotBlank(slave.getLabelString())) {
                        AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
                        if (slaveConfig != null) {
                            if (newCloud.getSlaveConfiguration(slaveConfig.getId()) == null) {
                                throw new FormException(MessageFormat.format("Cannot remove slave configuration ''{0}'' from ElasticBox cloud {1} because it is used by slave {2}.", 
                                        slaveConfig.getDescription(), existingCloud.getDisplayName(), slave.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
                            }
                        } else {
                            // this is for backward compatibility with older slaves that are not associated with slave configuration via id
                            Label label = null;
                            try {
                                label = Label.parseExpression(slave.getLabelString());
                            } catch (ANTLRException ex) {
                                Logger.getLogger(ElasticBoxCloud.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                            }
                            if (label != null && existingCloud.getSlaveConfiguration(label) != null && 
                                    newCloud.getSlaveConfiguration(label) == null) {
                                throw new FormException(MessageFormat.format("Cannot remove slave configuration with labels ''{0}'' from ElasticBox cloud {1} because it is used by slave {2}.",
                                        slave.getLabelString(), existingCloud.getDisplayName(), slave.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
                            }
                        }
                    }
                }
            }
        }
    }
    
    public static final ElasticBoxCloud getInstance() {
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud && cloud.name.indexOf('@') != -1) {
                return (ElasticBoxCloud) cloud;
            }
        }   
        
        return null;
    }
 
    public static class ConverterImpl extends RetentionTimeConverter<ElasticBoxCloud> {

        @Override
        protected void fixZeroRetentionTime(ElasticBoxCloud cloud) {
            if (cloud.getRetentionTime() == 0) {
                cloud.retentionTime = Integer.MAX_VALUE;
            }
            for (SlaveConfiguration slaveConfig : cloud.getSlaveConfigurations()) {
                if (slaveConfig.getRetentionTime() == 0) {
                    slaveConfig.retentionTime = Integer.MAX_VALUE;
                }
            }
        }
        
    }
}
