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
import com.elasticbox.jenkins.ElasticBoxItemProvider;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class StartBox extends Builder {
    private final String instance;
    private final String workspace;
    
    @DataBoundConstructor
    public StartBox(String workspace, String instance) {
        this.workspace = workspace;
        this.instance = instance;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ElasticBoxCloud cloud = ElasticBoxCloud.getInstance();
        if (cloud == null) {
            throw new IOException("No ElasticBox cloud is configured.");
        }
        IProgressMonitor monitor = cloud.createClient().poweron(instance);
        String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), monitor.getResourceUrl());
        listener.getLogger().println(MessageFormat.format("Starting box instance {0}", instancePageUrl));
        listener.getLogger().println(MessageFormat.format("Waiting for the box instance {0} to start", instancePageUrl));
        try {
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            listener.getLogger().println(MessageFormat.format("The box instance {0} has been started successfully ", instancePageUrl));
            return true;
        } catch (IProgressMonitor.IncompleteException ex) {
            Logger.getLogger(LaunchBox.class.getName()).log(Level.SEVERE, null, ex);
            listener.error("Failed to start box instance %s: %s", instancePageUrl, ex.getMessage());
            throw new AbortException(ex.getMessage());
        }
    }    

    public String getWorkspace() {
        return workspace;
    }

    public String getInstance() {
        return instance;
    }
    
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private final ElasticBoxItemProvider itemProvider = new ElasticBoxItemProvider();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox - Start Box";
        }
        
        public ListBoxModel doFillWorkspaceItems() {
            return itemProvider.getWorkspaces();
        }
        
        public ListBoxModel doFillInstanceItems(@QueryParameter String workspace, @QueryParameter String filter) {                
            return itemProvider.getInstances(workspace, filter);
        }
        
    }
}
