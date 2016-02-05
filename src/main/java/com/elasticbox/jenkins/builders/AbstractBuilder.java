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

import com.elasticbox.Constants;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.deployment.execution.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.model.AbstractProject;
import hudson.slaves.Cloud;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class AbstractBuilder extends Builder {
    private final String cloud;
    private final String workspace;

    public AbstractBuilder(String cloud, String workspace) {
        this.cloud = cloud;
        this.workspace = workspace;
    }

    public String getCloud() {
        return cloud;
    }

    public String getWorkspace() {
        return workspace;
    }

    public static abstract class AbstractBuilderDescriptor extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public ListBoxModel doFillCloudItems() {
            ListBoxModel clouds = new ListBoxModel(new ListBoxModel.Option(Constants.CHOOSE_CLOUD_MESSAGE, ""));
            for (Cloud cloud : Jenkins.getInstance().clouds) {
                if (cloud instanceof ElasticBoxCloud) {
                    clouds.add(cloud.getDisplayName(), cloud.name);
                }
            }

            return clouds;
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {
            final ListBoxModel workspaceOptions = DescriptorHelper.getEmptyListBoxModel(Constants.CHOOSE_WORKSPACE_MESSAGE, "");
            if (DescriptorHelper.anyOfThemIsBlank(cloud)) {
                return workspaceOptions;
            }

            final DeployBoxOrderResult<List<AbstractWorkspace>> result = new DeployBoxOrderServiceImpl(ClientCache.getClient(cloud)).getWorkspaces();
            final List<AbstractWorkspace> workspaces = result.getResult();
            for (AbstractWorkspace workspace : workspaces) {
                workspaceOptions.add(workspace.getName(), workspace.getId());
            }

            return workspaceOptions;
        }

        public FormValidation doCheckCloud(@QueryParameter String value) {
            return DescriptorHelper.checkCloud(value);
        }

        public FormValidation doCheckWorkspace(@QueryParameter String workspace) {
            if (StringUtils.isBlank(workspace)) {
                return FormValidation.error("Workspace is required");
            }
            return FormValidation.ok();
        }

    }
}
