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
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import net.sf.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Phong Nguyen Le
 * @deprecated
 */
public class ReinstallBox extends InstanceBuildStep {

    @DataBoundConstructor
    public ReinstallBox(String cloud, String workspace, String box, String instance, String buildStep) {
        super(cloud, workspace, box, instance, buildStep);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Reinstall Box build step");

        IInstanceProvider instanceProvider = getInstanceProvider(build);
        if (instanceProvider == null || instanceProvider.getElasticBoxCloud() == null) {
            throw new IOException("No valid ElasticBox cloud is selected for this build step.");
        }

        ElasticBoxCloud ebCloud = instanceProvider.getElasticBoxCloud();
        reinstall(instanceProvider.getInstanceId(build), ebCloud.getClient(), null, true, logger);
        return true;
    }

    static void reinstall(String instanceId, Client client, JSONArray jsonVariables,
            boolean waitForCompletion, TaskLogger logger) throws IOException, InterruptedException {
        IProgressMonitor monitor = client.reinstall(instanceId,
                DescriptorHelper.removeInvalidVariables(jsonVariables, instanceId, client));
        String instancePageUrl = Client.getPageUrl(client.getEndpointUrl(), monitor.getResourceUrl());
        logger.info(MessageFormat.format("Reinstalling box instance {0}", instancePageUrl));
        if (waitForCompletion) {
            logger.info("Waiting for the instance to finish reinstall");
            LongOperation.waitForCompletion(Client.InstanceOperation.REINSTALL, Collections.singletonList(monitor),
                    client, logger, ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
        }
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox - Reinstall Box";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return false;
        }

    }
}
