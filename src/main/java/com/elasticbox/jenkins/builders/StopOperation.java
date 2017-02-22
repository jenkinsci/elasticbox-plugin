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
import com.elasticbox.jenkins.util.Condition;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StopOperation extends LongOperation implements IOperation.InstanceOperation {

    public static final int AVAILABILITY_TIMEOUT_SECONDS = 20;

    @DataBoundConstructor
    public StopOperation(String tags, boolean waitForCompletion, int waitForCompletionTimeout) {
        super(tags, waitForCompletion, waitForCompletionTimeout);
    }

    public void perform(
        ElasticBoxCloud cloud,
        String workspace,
        AbstractBuild<?, ?> build,
        Launcher launcher,
        TaskLogger logger) throws InterruptedException, IOException {

        logger.info(MessageFormat.format("Executing {0}", getDescriptor().getDisplayName()));

        VariableResolver resolver = new VariableResolver(cloud.name, workspace, build, logger.getTaskListener());
        Client client = cloud.getClient();
        Set<String> resolvedTags = resolver.resolveTags(getTags());

        logger.info("Looking for instances with the following tags: " + StringUtils.join(resolvedTags, ", "));

        JSONArray instances = DescriptorHelper.getInstances(resolvedTags, cloud.name, workspace, true);
        if (!canPerform(instances, logger)) {
            return;
        }

        List<IProgressMonitor> monitors = new ArrayList<IProgressMonitor>();
        for (Object instance : instances) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            JSONObject instanceJson = (JSONObject) instance;
            String instanceId = instanceJson.getString("id");

            if ( !waitForAvailable(client, instanceId, AVAILABILITY_TIMEOUT_SECONDS)) {
                logger.info("WARNING: Instance {0} not available in {1} seconds to execute Stop operation",
                        instanceId, AVAILABILITY_TIMEOUT_SECONDS);
            }

            IProgressMonitor monitor = client.shutdown(instanceId);

            monitors.add(monitor);
            String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), instanceJson);
            logger.info(MessageFormat.format("Stopping instance {0}", instancePageUrl));
        }

        if (isWaitForCompletion()) {
            logger.info(
                MessageFormat.format(
                    "Waiting for {0} to complete stopping",
                    instances.size() > 1
                        ? "the instances"
                        : "the instance"));

            LongOperation.waitForCompletion(
                getDescriptor().getDisplayName(),
                monitors,
                client,
                logger,
                getWaitForCompletionTimeout());
        }
    }

    @Override
    protected boolean failIfNoInstanceFound() {
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends OperationDescriptor {

        @Override
        public String getDisplayName() {
            return "Stop";
        }

    }

    protected boolean waitForAvailable(final Client client, final String instanceId, long timeoutSeconds) {
        return new Condition() {

            public boolean satisfied() {
                JSONObject instanceJson = null;
                try {
                    instanceJson = client.getInstance(instanceId);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
                return Client.FINISH_STATES.contains(instanceJson.getString("state"));
            }

        }.waitUntilSatisfied(timeoutSeconds);
    }
}
