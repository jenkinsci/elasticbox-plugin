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
import com.elasticbox.jenkins.util.CompositeObjectFilter;
import com.elasticbox.jenkins.util.ObjectFilter;
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
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Phong Nguyen Le
 */
public class ReconfigureOperation extends LongOperation implements IOperation.InstanceOperation {

    @DataBoundConstructor
    public ReconfigureOperation(String tags, boolean failIfNoneFound, boolean waitForCompletion, int waitForCompletionTimeout) {
        super(tags, failIfNoneFound, waitForCompletion, waitForCompletionTimeout);
    }

    public void perform(ElasticBoxCloud cloud, String workspace, AbstractBuild<?, ?> build, Launcher launcher,
            TaskLogger logger) throws InterruptedException, IOException {
        logger.info("Executing Reconfigure");
        
        VariableResolver resolver = new VariableResolver(cloud.name, workspace, build, logger.getTaskListener());
        Client client = cloud.getClient();
        Set<String> resolvedTags = resolver.resolveTags(getTags());
        logger.info(MessageFormat.format("Looking for instances with the following tags: {0}", 
                StringUtils.join(resolvedTags, ", ")));
        JSONArray instances = DescriptorHelper.getInstances(client, workspace, instanceFilter(resolvedTags));        
        if (!canPerform(instances, logger)) {
            return;
        }

        reconfigure(instances, null, getWaitForCompletionTimeout(), cloud.getClient(), logger);
    }
    
    static void reconfigure(JSONArray instances, JSONArray variables, int waitForCompletionTimeout, Client client,
            TaskLogger logger) throws InterruptedException, IOException {
        List<IProgressMonitor> monitors = new ArrayList<IProgressMonitor>();        
        for (Object instance : instances) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }            
            JSONObject instanceJson = (JSONObject) instance;
            String instanceId = instanceJson.getString("id");
            IProgressMonitor monitor = client.reconfigure(instanceId, 
                    DescriptorHelper.removeInvalidVariables(variables, instanceId, client));
            monitors.add(monitor);
            String instancePageUrl = Client.getPageUrl(client.getEndpointUrl(), instanceJson);
            logger.info(MessageFormat.format("Reconfiguring box instance {0}", instancePageUrl));            
        }
        if (waitForCompletionTimeout > 0) {
            logger.info(MessageFormat.format("Waiting for {0} to finish reconfiguration", 
                    instances.size() > 1 ? "the instances" : "the instance"));
            LongOperation.waitForCompletion(DescriptorImpl.DISPLAY_NAME, monitors, client, logger, waitForCompletionTimeout);
        }
        
    }
    
    public static final ObjectFilter instanceFilter(Set<String> tags) {
        return new CompositeObjectFilter(new DescriptorHelper.InstanceFilterByTags(tags, false),
            new ObjectFilter() {

            public boolean accept(JSONObject instance) {
                // reject inaccessible instances that cannot be reconfigured                
                String operation = instance.getString("operation");
                if (Client.InstanceState.UNAVAILABLE.equals(instance.getString("state")) && 
                        Client.InstanceOperation.RECONFIGURE.equals(operation)) {
                    return true;
                }
                return !Client.TERMINATE_OPERATIONS.contains(operation);
            }
        });
    }
    
    @Extension
    public static final class DescriptorImpl extends OperationDescriptor {
        private static String DISPLAY_NAME = "Reconfigure";

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }
        
    }
    
}
