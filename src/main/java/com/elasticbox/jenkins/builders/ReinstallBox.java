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
import hudson.model.BuildListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Phong Nguyen Le
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
        reinstall(instanceProvider.getInstanceId(build), ebCloud, ebCloud.createClient(), null, true, logger);
        return true;
    }   
    
    static void reinstall(String instanceId, ElasticBoxCloud ebCloud, Client client, JSONArray jsonVariables,
            boolean waitForCompletion, TaskLogger logger) throws IOException {
        IProgressMonitor monitor = client.reinstall(instanceId, jsonVariables);
        String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl());
        logger.info("Reinstalling box instance {0}", instancePageUrl);
        if (waitForCompletion) {
            try {
                logger.info("Waiting for the box instance {0} to finish reinstall", instancePageUrl);
                monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
                logger.info("The box instance {0} has been reinstalled successfully ", instancePageUrl);
            } catch (IProgressMonitor.IncompleteException ex) {
                Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                logger.error("Failed to reinstall box instance {0}: {1}", instancePageUrl, ex.getMessage());
                throw new AbortException(ex.getMessage());
            }   
        }
    }
    
    static void reinstall(List<String> instanceIDs, ElasticBoxCloud ebCloud, Client client, JSONArray jsonVariables, 
            boolean waitForCompletion, TaskLogger logger) throws IOException {
        List<IProgressMonitor> monitors = new ArrayList<IProgressMonitor>();
        for (String instanceId : instanceIDs) {
            IProgressMonitor monitor = client.reinstall(instanceId, jsonVariables);
            monitors.add(monitor);
            String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl());
            logger.info(MessageFormat.format("Reinstalling box instance {0}", instancePageUrl));            
        }
        if (waitForCompletion) {
            logger.info(MessageFormat.format("Waiting for {0} to finish reinstall", instanceIDs.size() > 0 ? "the instances" : "the instance"));
            long startWaitTime = System.currentTimeMillis();
            List<IProgressMonitor> doneMonitors = new ArrayList<IProgressMonitor>();
            final int waitInterval = 2;            
            final int maxWaitCycles = (int) Math.ceil(ElasticBoxSlaveHandler.TIMEOUT_MINUTES / (waitInterval * instanceIDs.size())) + 1; 
            for (int i = 0; i < maxWaitCycles && doneMonitors.size() < instanceIDs.size(); i++) {
                for (Iterator<IProgressMonitor> iter = monitors.iterator(); iter.hasNext();) {
                    IProgressMonitor monitor = iter.next();
                    String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl());
                    try {
                        monitor.waitForDone(waitInterval);
                        doneMonitors.add(monitor);
                        iter.remove();
                        logger.info(MessageFormat.format("The box instance {0} has been reinstalled successfully ", instancePageUrl));
                    } catch (IProgressMonitor.TimeoutException ex) {
                        logger.info(ex.getMessage());
                    } catch (IProgressMonitor.IncompleteException ex) {
                        Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, null, ex);
                        logger.error("Failed to reinstall box instance {0}: {1}", instancePageUrl, ex.getMessage());
                        throw new AbortException(ex.getMessage());
                    }
                }
            }
            if (!monitors.isEmpty()) {
                List<String> instancePageURLs = new ArrayList<String>();
                for (IProgressMonitor monitor : monitors) {
                    instancePageURLs.add(Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl()));
                }
                String message = MessageFormat.format("The following instances still are not ready after waiting for {0} minutes: {1}", 
                        TimeUnit.MINUTES.toMinutes(System.currentTimeMillis() - startWaitTime), StringUtils.join(instancePageURLs, ','));
                logger.error(message);
                throw new AbortException(message);
            }
        }
    }
    

    @Extension
    public static final class DescriptorImpl extends Descriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox - Reinstall Box";
        }

    }
}
