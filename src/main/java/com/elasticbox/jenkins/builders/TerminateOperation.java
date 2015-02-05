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

package com.elasticbox.jenkins.builders;

import com.elasticbox.Client;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Phong Nguyen Le
 */
public class TerminateOperation extends LongOperation implements IOperation.InstanceOperation {

    private static void notifyTerminating(AbstractBuild<?, ?> build, String instanceId, ElasticBoxCloud cloud) 
            throws InterruptedException {
        for (BuilderListener listener: Jenkins.getInstance().getExtensionList(BuilderListener.class)) {
            try {
                listener.onDeploying(build, instanceId, cloud);
            } catch (IOException ex) {
                Logger.getLogger(TerminateOperation.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }
    
    private final boolean delete;
    private final boolean force;

    @DataBoundConstructor
    public TerminateOperation(String tags, boolean waitForCompletion, int waitForCompletionTimeout, boolean force, boolean delete) {
        super(tags, false, waitForCompletion, waitForCompletionTimeout);
        this.delete = delete;
        this.force = force;
    }

    public boolean isDelete() {
        return delete;
    }

    public boolean isForce() {
        return force;
    }

    public void perform(ElasticBoxCloud cloud, String workspace, AbstractBuild<?, ?> build, Launcher launcher,
            TaskLogger logger) throws InterruptedException, IOException {
        logger.info("Executing Terminate");

        VariableResolver resolver = new VariableResolver(cloud.name, workspace, build, logger.getTaskListener());
        Client client = cloud.getClient();
        Set<String> resolvedTags = resolver.resolveTags(getTags());
        logger.info(MessageFormat.format("Looking for instances with the following tags: {0}", 
                StringUtils.join(resolvedTags, ", ")));
        JSONArray instances = DescriptorHelper.getInstances(resolvedTags, cloud.name, workspace, false);        
        if (!canPerform(instances, logger)) {
            return;
        }

        List<String> instanceIDs = terminate(instances, getWaitForCompletionTimeout(), isForce(), cloud, logger, build);
        
        if (isDelete()) {
            logger.info(MessageFormat.format("Deleting terminated {0}", 
                    instances.size() > 1 ? "instances" : "instance"));
            for (String instanceId : instanceIDs) {
                client.delete(instanceId);
            }
        }
    }
    
    static List<String> terminate(JSONArray instances, int waitForCompletionTimeout, boolean force, 
            ElasticBoxCloud cloud, TaskLogger logger, AbstractBuild<?, ?> build)
            throws InterruptedException, IOException {
        Client client = cloud.getClient();
        List<IProgressMonitor> monitors = new ArrayList<IProgressMonitor>();
        List<String> instanceIDs = new ArrayList<String>();
        for (Object instance : instances) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            JSONObject instanceJson = (JSONObject) instance;
            String instanceId = instanceJson.getString("id");
            instanceIDs.add(instanceId);
            String instancePageUrl = Client.getPageUrl(client.getEndpointUrl(), instanceJson);
            if (Client.TERMINATE_OPERATIONS.contains(instanceJson.getString("operation")) && 
                Client.InstanceState.DONE.equals(instanceJson.getString("state"))) {
                logger.info(MessageFormat.format("Instance {0} is already terminated", instancePageUrl));
                continue;
            }
            IProgressMonitor monitor = force ? client.forceTerminate(instanceId) : client.terminate(instanceId);
            monitors.add(monitor);
            logger.info(MessageFormat.format(force ? "Force-terminating instance {0}" : "Terminating instance {0}", instancePageUrl));
            notifyTerminating(build, instanceId, cloud);
        }
        
        if (!monitors.isEmpty() && waitForCompletionTimeout > 0) {
            logger.info(MessageFormat.format("Waiting for {0} to complete terminating", 
                    instances.size() > 1 ? "the instances" : "the instance"));
            LongOperation.waitForCompletion(DescriptorImpl.DISPLAY_NAME, monitors, client, logger, waitForCompletionTimeout);
        }    
        
        return instanceIDs;
    }
    
    static void terminate(JSONObject instance, Client client, TaskLogger logger) 
            throws IOException, InterruptedException {
        String instanceId = instance.getString("id");
        IProgressMonitor monitor = client.terminate(instanceId);
        String instancePageUrl = Client.getPageUrl(client.getEndpointUrl(), instance);
        logger.info(MessageFormat.format("Terminating box instance {0}", instancePageUrl));
        try {
            logger.info(MessageFormat.format("Waiting for the box instance {0} to be terminated", instancePageUrl));
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            logger.info(MessageFormat.format("The box instance {0} has been terminated successfully ", 
                    instancePageUrl));
        } catch (IProgressMonitor.IncompleteException ex) {
            logger.info(ex.getMessage());
            monitor = client.forceTerminate(instanceId);
            logger.info(MessageFormat.format("Force-terminating instance {0}", instancePageUrl));
            try {
                logger.info(MessageFormat.format("Waiting for the box instance {0} to be force-terminated", instancePageUrl));
                monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
                logger.info(MessageFormat.format("The box instance {0} has been force-terminated successfully ",
                        instancePageUrl));
            } catch (IProgressMonitor.IncompleteException e) {
                Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, null, e);
                logger.error("Failed to terminate box instance {0}: {1}", instancePageUrl, ex.getMessage());
                throw new AbortException(ex.getMessage());
            }
        }
    }
    
    
    @Extension
    public static final class DescriptorImpl extends OperationDescriptor {
        private static final String DISPLAY_NAME = "Terminate";

        @Override
        public String getDisplayName() {
            return "Terminate";
        }
        
    }
    
}
