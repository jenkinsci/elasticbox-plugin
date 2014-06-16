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
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.Scrambler;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
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
    
    @DataBoundConstructor
    public ElasticBoxCloud(String endpointUrl, int maxInstances, int retentionTime, String username, String password) {
        super(username + "@" + endpointUrl, String.valueOf(maxInstances));
        this.endpointUrl = endpointUrl;
        this.maxInstances = maxInstances;
        this.retentionTime = retentionTime;
        this.username = username;
        this.password = Scrambler.scramble(password);
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        // readjust the excess work load by considering the instances that are being deployed or already deployed but not yet connectd with Jenkins
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (ElasticBoxSlaveHandler.isSubmitted(slave)) {
                    excessWorkload -= slave.getNumExecutors();
                } else if (label.matches(slave) && slave.getComputer().isOffline() && slave.getInstanceId() != null) {
                    try {
                        JSONObject instance = slave.getInstance();
                        String state = instance.getString("state");
                        String operation = instance.getString("operation");
                        if (Client.ON_OPERATIONS.contains(operation) &&
                                (Client.InstanceState.PROCESSING.equals(state) || Client.InstanceState.DONE.equals(state))) {
                            excessWorkload -= slave.getNumExecutors();
                        }
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }
        }
        
        List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();
        while (excessWorkload > 0) {
            try {
                if (ElasticBoxSlaveHandler.countInstances() >= maxInstances) {
                    LOGGER.log(Level.WARNING, "Max number of ElasticBox instances has been reached");
                    break;
                }
                
                String profileId = label.getName().substring(ElasticBoxLabelFinder.REUSE_PREFIX.length());
                final ElasticBoxSlave slave = new ElasticBoxSlave(profileId, false, this);
                
                plannedNodes.add(new NodeProvisioner.PlannedNode(slave.getDisplayName(),
                        Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                            public Node call() throws Exception {
                                slave.setInUse(true);
                                Jenkins.getInstance().addNode(slave);                                
                                IProgressMonitor monitor = ElasticBoxSlaveHandler.submit(slave);
                                monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
                                if (slave.getComputer() != null && slave.getComputer().isOnline()) {
                                    return slave;
                                } else {
                                    throw new Exception(MessageFormat.format("Cannot deploy slave {0}. See the system log for more details.", slave.getDisplayName()));
                                }                                
                            }
                        }), 1));
                
                excessWorkload -= slave.getNumExecutors();
            } catch (Descriptor.FormException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        return plannedNodes;
    }

    @Override
    public boolean canProvision(Label label) {
        return label != null && label.getName() != null && label.getName().startsWith(ElasticBoxLabelFinder.REUSE_PREFIX);
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
