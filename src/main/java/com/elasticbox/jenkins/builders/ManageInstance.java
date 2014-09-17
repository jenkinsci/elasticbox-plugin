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

import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.util.TaskLogger;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.slaves.Cloud;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class ManageInstance extends Builder {
    private final String cloud;
    private final String workspace;
    private final List<? extends Operation> operations;
    
    @DataBoundConstructor
    public ManageInstance(String cloud, String workspace, List<? extends Operation> operations) {
        this.cloud = cloud;
        this.workspace = workspace;   
        this.operations = operations;
    }

    public String getCloud() {
        return cloud;
    }

    public String getWorkspace() {
        return workspace;
    }

    public List<? extends Operation> getOperations() {
        return operations != null ? Collections.unmodifiableList(operations) : Collections.EMPTY_LIST;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Manage Instance build step");

        Cloud ebCloud = Jenkins.getInstance().getCloud(cloud);
        if (!(ebCloud instanceof ElasticBoxCloud)) {
            throw new IOException(MessageFormat.format("Invalid cloud name: {0}", cloud));
        }
        
        for (Operation operation : operations) {
            operation.perform((ElasticBoxCloud) ebCloud, workspace, build, launcher, logger);
        }
        
        return true;
    }
    
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox - Manage Instance";
        }
        
        public ListBoxModel doFillCloudItems() {
            return DescriptorHelper.getClouds();
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {
            return DescriptorHelper.getWorkspaces(cloud);
        }
        
        public FormValidation doCheckCloud(@QueryParameter String value) {
            return DescriptorHelper.checkCloud(value);
        }

        public DescriptorExtensionList<Operation, Operation.OperationDescriptor> getOperations() {
            return Jenkins.getInstance().getDescriptorList(Operation.class);
        }
        
    }
}
