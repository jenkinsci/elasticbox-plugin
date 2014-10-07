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
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Phong Nguyen Le
 */
public class StartBox extends InstanceBuildStep {

    @DataBoundConstructor
    public StartBox(String cloud, String workspace, String box, String instance, String buildStep) {
        super(cloud, workspace, box, instance, buildStep);
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Start Box build step");

        IInstanceProvider instanceProvider = getInstanceProvider(build);
        if (instanceProvider == null || instanceProvider.getElasticBoxCloud() == null) {
            throw new IOException("No valid ElasticBox cloud is selected for this build step.");
        }
        
        ElasticBoxCloud ebCloud = instanceProvider.getElasticBoxCloud();
        IProgressMonitor monitor = ebCloud.getClient().poweron(instanceProvider.getInstanceId(build));
        String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl());
        logger.info(MessageFormat.format("Starting box instance {0}", instancePageUrl));
        logger.info(MessageFormat.format("Waiting for the box instance {0} to start", instancePageUrl));
        try {
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            logger.info(MessageFormat.format("The box instance {0} has been started successfully ", instancePageUrl));
            return true;
        } catch (IProgressMonitor.IncompleteException ex) {
            Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            logger.error(MessageFormat.format("Failed to start box instance {0}: {1}", instancePageUrl, ex.getMessage()));
            throw new AbortException(ex.getMessage());
        }
    }    

    @Extension
    public static final class DescriptorImpl extends Descriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox - Start Box";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return false;
        }

    }
}
