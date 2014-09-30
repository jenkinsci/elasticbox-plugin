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
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Phong Nguyen Le
 */
public class TerminateOperation extends LongOperation implements IOperation.InstanceOperation {
    private final boolean delete;

    @DataBoundConstructor
    public TerminateOperation(String tags, boolean waitForCompletion, boolean delete) {
        super(tags, waitForCompletion);
        this.delete = delete;
    }

    public boolean isDelete() {
        return delete;
    }

    @Override
    public void perform(ElasticBoxCloud cloud, String workspace, AbstractBuild<?, ?> build, Launcher launcher, TaskLogger logger) throws InterruptedException, IOException {
        logger.info("Executing Terminate");
        
        VariableResolver resolver = new VariableResolver(cloud.name, workspace, build, logger.getTaskListener());
        Client client = ClientCache.getClient(cloud.name);
        Set<String> resolvedTags = resolver.resolveTags(getTags());
        logger.info(MessageFormat.format("Looking for instances with the following tags: {0}", StringUtils.join(resolvedTags, ", ")));
        JSONArray instances = DescriptorHelper.getInstances(resolvedTags, cloud.name, workspace, false);        
        if (instances.isEmpty()) {
            logger.info("No instance found with the specified tags");
            return;
        }

        List<IProgressMonitor> monitors = new ArrayList<IProgressMonitor>();
        List<String> instanceIDs = new ArrayList<String>();
        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;
            String instanceId = instanceJson.getString("id");
            instanceIDs.add(instanceId);
            String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), instanceJson);
            if (Client.TERMINATE_OPERATIONS.contains(instanceJson.getString("operation")) && 
                Client.InstanceState.DONE.equals(instanceJson.getString("state"))) {
                logger.info(MessageFormat.format("Instance {0} is already terminated", instancePageUrl));
                continue;
            }
            IProgressMonitor monitor = client.terminate(instanceId);
            monitors.add(monitor);
            logger.info(MessageFormat.format("Terminating instance {0}", instancePageUrl));            
        }

        if (!monitors.isEmpty() && isWaitForCompletion()) {
            logger.info(MessageFormat.format("Waiting for {0} to complete terminating", instances.size() > 1 ? "the instances" : "the instance"));
            InstanceBuildStep.waitForCompletion(getDescriptor().getDisplayName(), monitors, cloud, client, logger);
        }
        if (isDelete()) {
            logger.info(MessageFormat.format("Deleting terminated {0}", instances.size() > 1 ? "instances" : "instance"));
            for (String instanceId : instanceIDs) {
                client.delete(instanceId);
            }
        }
    }
    
    @Extension
    public static final class DescriptorImpl extends OperationDescriptor {

        @Override
        public String getDisplayName() {
            return "Terminate";
        }
        
    }
    
}
