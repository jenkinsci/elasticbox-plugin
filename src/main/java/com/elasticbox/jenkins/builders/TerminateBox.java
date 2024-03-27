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
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.TaskLogger;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Terminates a box instance.
 *
 * @author Phong Nguyen Le.
 * @deprecated should use TerminateOperation instead
 */
public class TerminateBox extends InstanceBuildStep {
    private final boolean delete;

    @DataBoundConstructor
    public TerminateBox(String cloud, String workspace, String box, String instance, String buildStep, boolean delete) {
        super(cloud, workspace, box, instance, buildStep);
        this.delete = delete;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Terminate Box build step");

        IInstanceProvider instanceProvider = getInstanceProvider(build);
        if (instanceProvider == null || instanceProvider.getElasticBoxCloud() == null) {
            throw new IOException("No valid ElasticBox cloud is selected for this build step.");
        }

        ElasticBoxCloud ebCloud = instanceProvider.getElasticBoxCloud();
        Client client = ebCloud.getClient();
        String instanceId = instanceProvider.getInstanceId(build);
        terminate(instanceId, client, logger);
        if (delete) {
            client.delete(instanceId);
        }

        return true;
    }

    public boolean isDelete() {
        return delete;
    }

    static void terminate(String instanceId, Client client, TaskLogger logger)
            throws IOException, InterruptedException {
        IProgressMonitor monitor = client.terminate(instanceId);
        String instancePageUrl = Client.getPageUrl(client.getEndpointUrl(), monitor.getResourceUrl());
        logger.info(MessageFormat.format("Terminating box instance {0}", instancePageUrl));
        try {
            logger.info(MessageFormat.format("Waiting for the box instance {0} to be terminated", instancePageUrl));
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            logger.info(MessageFormat.format("The box instance {0} has been terminated successfully ",
                    instancePageUrl));
        } catch (IProgressMonitor.IncompleteException ex) {
            Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, null, ex);
            logger.error("Failed to terminate box instance %s: %s", instancePageUrl, ex.getMessage());
            throw new AbortException(ex.getMessage());
        }
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox - Terminate Box";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return false;
        }

    }
}
