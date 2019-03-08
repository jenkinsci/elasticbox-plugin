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

package com.elasticbox.jenkins;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.model.services.deployment.execution.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;
import com.elasticbox.jenkins.util.ClientCache;

import hudson.Extension;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;


public class ProjectSlaveConfiguration extends AbstractSlaveConfiguration {

    private static final Logger LOGGER = Logger.getLogger(ProjectSlaveConfiguration.class.getName());

    static final String SLAVE_CONFIGURATION = "slaveConfiguration";

    private final String cloud;

    @DataBoundConstructor
    public ProjectSlaveConfiguration(
        String id, String cloud, String workspace, String box, String boxVersion, String profile, String claims,
        String provider, String location, int maxInstances, String tags, String variables, String remoteFs,
        int retentionTime, String maxBuildsText, int executors, int launchTimeout, String boxDeploymentType) {

        super(StringUtils.isBlank(id) ? UUID.randomUUID().toString() : id, workspace, box, boxVersion, profile,
                claims, provider, location, 0,
                maxInstances, tags, variables, null, StringUtils.EMPTY, remoteFs, Node.Mode.EXCLUSIVE,
                retentionTime, StringUtils.isBlank(maxBuildsText) ? 0 : Integer.parseInt(maxBuildsText), executors,
                launchTimeout, boxDeploymentType);

        this.cloud = cloud;
    }

    public String getCloud() {
        return cloud;
    }

    public ElasticBoxCloud getElasticBoxCloud() {
        Cloud cloud = Jenkins.get().getCloud(this.cloud);
        return (cloud instanceof ElasticBoxCloud ? (ElasticBoxCloud) cloud : null);
    }

    public static List<ProjectSlaveConfiguration> list() {

        List<ProjectSlaveConfiguration> slaveConfigurations = new ArrayList<ProjectSlaveConfiguration>();

        List<BuildableItemWithBuildWrappers> projects = Jenkins.get()
            .getItems(BuildableItemWithBuildWrappers.class);

        for (BuildableItemWithBuildWrappers project : projects) {
            for (Object buildWrapper : project.getBuildWrappersList().toMap().values()) {
                if (buildWrapper instanceof InstanceCreator) {
                    slaveConfigurations.add(((InstanceCreator) buildWrapper).getSlaveConfiguration());
                }
            }
        }
        return slaveConfigurations;
    }

    public static ProjectSlaveConfiguration find(String id) {
        for (ProjectSlaveConfiguration slaveConfig : list()) {
            if (slaveConfig.getId().equals(id)) {
                return slaveConfig;
            }
        }
        return null;
    }

    public static ProjectSlaveConfiguration find(Label label) {
        String prefix = null;
        if (label.getName().startsWith(ElasticBoxLabelFinder.REUSE_PREFIX)) {
            prefix = ElasticBoxLabelFinder.REUSE_PREFIX;
        } else if (label.getName().startsWith(ElasticBoxLabelFinder.SINGLE_USE_PREFIX)) {
            prefix = ElasticBoxLabelFinder.SINGLE_USE_PREFIX;
        }

        if (prefix != null) {
            String configId = label.getName().substring(prefix.length());
            return find(configId);
        }

        return null;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractSlaveConfigurationDescriptor {

        @Override
        public String getDisplayName() {
            return "Per-project Slave Configuration";
        }

        public ListBoxModel doFillCloudItems() {
            ListBoxModel clouds = new ListBoxModel(new ListBoxModel.Option(Constants.CHOOSE_CLOUD_MESSAGE, ""));
            for (Cloud cloud : Jenkins.get().clouds) {
                if (cloud instanceof ElasticBoxCloud) {
                    clouds.add(cloud.getDisplayName(), cloud.name);
                }
            }

            return clouds;
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {

            final ListBoxModel workspaceOptions =
                DescriptorHelper.getEmptyListBoxModel(Constants.CHOOSE_WORKSPACE_MESSAGE, "");

            if (DescriptorHelper.anyOfThemIsBlank(cloud)) {
                return workspaceOptions;
            }

            try {
                final DeployBoxOrderResult<List<AbstractWorkspace>> result =
                    new DeployBoxOrderServiceImpl(ClientCache.getClient(cloud)).getWorkspaces();

                final List<AbstractWorkspace> workspaces = result.getResult();

                for (AbstractWorkspace workspace : workspaces) {
                    workspaceOptions.add(workspace.getName(), workspace.getId());
                }

                return workspaceOptions;

            } catch (ServiceException e) {
                return workspaceOptions;
            }
        }

        public ListBoxModel doFillBoxItems(@QueryParameter String cloud, @QueryParameter String workspace) {

            ListBoxModel boxes = DescriptorHelper.getEmptyListBoxModel(Constants.CHOOSE_BOX_MESSAGE, "");
            if (DescriptorHelper.anyOfThemIsBlank(cloud, workspace)) {
                return boxes;
            }

            try {
                final DeployBoxOrderServiceImpl deployBoxOrderService =
                    new DeployBoxOrderServiceImpl(ClientCache.getClient(cloud));

                final AbstractWorkspace workspaceModel =
                    deployBoxOrderService.findWorkspaceOrFirstByDefault(workspace).getResult();

                final List<AbstractBox> boxesToDeploy =
                    deployBoxOrderService.getBoxesToDeploy(workspaceModel.getId()).getResult();

                for (AbstractBox box : boxesToDeploy) {
                    boxes.add(box.getName(),box.getId());
                }

                Collections.sort(boxes, new Comparator<ListBoxModel.Option>() {
                    public int compare(ListBoxModel.Option o1, ListBoxModel.Option o2) {
                        return o1.name.compareTo(o2.name);
                    }
                });
                return boxes;
            } catch (ServiceException e) {
                return boxes;
            }

        }

        public ListBoxModel doFillBoxDeploymentTypeItems(
            @QueryParameter String cloud,
            @QueryParameter String workspace,
            @QueryParameter String box) {

            ListBoxModel boxDeploymentType =
                DescriptorHelper.getEmptyListBoxModel(Constants.CHOOSE_DEPLOYMENT_TYPE_MESSAGE, "");

            if (DescriptorHelper.anyOfThemIsBlank(cloud, workspace, box)) {
                return boxDeploymentType;
            }

            try {
                final DeployBoxOrderServiceImpl deployBoxOrderService =
                    new DeployBoxOrderServiceImpl(ClientCache.getClient(cloud));

                final AbstractWorkspace workspaceModel =
                    deployBoxOrderService.findWorkspaceOrFirstByDefault(workspace).getResult();

                final AbstractBox boxToDeploy =
                    deployBoxOrderService.findBoxOrFirstByDefault(workspaceModel.getId(), box).getResult();

                final DeploymentType deploymentType = deployBoxOrderService.deploymentType(boxToDeploy.getId());

                final String id = deploymentType.getValue();

                boxDeploymentType.add(new ListBoxModel.Option(id,id,true));

                return boxDeploymentType;

            } catch (ServiceException e) {
                return boxDeploymentType;
            }
        }

        public ListBoxModel doFillBoxVersionItems(@QueryParameter String cloud,
                                                  @QueryParameter String workspace,
                                                  @QueryParameter String box) {

            ListBoxModel boxVersions = DescriptorHelper.getEmptyListBoxModel("Latest", "LATEST");
            if (DescriptorHelper.anyOfThemIsBlank(cloud, workspace, box)) {
                return boxVersions;
            }

            try {
                final DeployBoxOrderServiceImpl deployBoxOrderService =
                    new DeployBoxOrderServiceImpl(ClientCache.getClient(cloud));

                final AbstractWorkspace workspaceModel =
                    deployBoxOrderService.findWorkspaceOrFirstByDefault(workspace).getResult();

                final AbstractBox boxToDeploy =
                    deployBoxOrderService.findBoxOrFirstByDefault(workspaceModel.getId(), box).getResult();

                return DescriptorHelper.getBoxVersions(cloud, workspaceModel.getId(), boxToDeploy.getId());

            } catch (ServiceException e) {
                return boxVersions;
            }
        }

        public ListBoxModel doFillProfileItems(
            @QueryParameter String cloud,
            @QueryParameter String workspace,
            @QueryParameter String box) {

            ListBoxModel profiles = DescriptorHelper.getEmptyListBoxModel(Constants.CHOOSE_POLICY_MESSAGE, "");
            if (DescriptorHelper.anyOfThemIsBlank(cloud, workspace, box)) {
                return profiles;
            }
            try {
                final DeployBoxOrderServiceImpl deployBoxOrderService =
                    new DeployBoxOrderServiceImpl(ClientCache.getClient(cloud));

                final AbstractWorkspace workspaceModel =
                    deployBoxOrderService.findWorkspaceOrFirstByDefault(workspace).getResult();

                final AbstractBox boxToDeploy =
                    deployBoxOrderService.findBoxOrFirstByDefault(workspaceModel.getId(), box).getResult();

                final List<PolicyBox> policies =
                    deployBoxOrderService.deploymentPolicies(workspaceModel.getId(), boxToDeploy.getId()).getResult();

                for (PolicyBox policyBox : policies) {
                    profiles.add(policyBox.getName(), policyBox.getId());
                }

                return profiles;

            } catch (ServiceException e) {
                return profiles;
            }
        }

        public ListBoxModel doFillProviderItems(@QueryParameter String cloud, @QueryParameter String workspace) {

            ListBoxModel providers = DescriptorHelper.getEmptyListBoxModel(Constants.CHOOSE_PROVIDER_MESSAGE, "");
            if (DescriptorHelper.anyOfThemIsBlank(cloud, workspace)) {
                return providers;
            }

            try {
                final DeployBoxOrderServiceImpl deployBoxOrderService =
                    new DeployBoxOrderServiceImpl(ClientCache.getClient(cloud));

                final AbstractWorkspace workspaceModel =
                    deployBoxOrderService.findWorkspaceOrFirstByDefault(workspace).getResult();

                return DescriptorHelper.getCloudFormationProviders(
                    ClientCache.getClient(cloud), workspaceModel.getId());

            } catch (ServiceException e) {
                return providers;
            }

        }

        public ListBoxModel doFillLocationItems(@QueryParameter String cloud, @QueryParameter String provider) {
            ListBoxModel locations = getEmptyListBoxModel(Constants.CHOOSE_REGION_MESSAGE, "");
            if (anyOfThemIsBlank(cloud, provider)) {
                return locations;
            }

            return DescriptorHelper.getCloudFormationLocations(ClientCache.getClient(cloud), provider);
        }

        public FormValidation doCheckCloud(@QueryParameter String cloud) {
            if (StringUtils.isBlank(cloud)) {
                return FormValidation.error("Cloud is required");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckWorkspace(@QueryParameter String workspace) {
            if (StringUtils.isBlank(workspace)) {
                return FormValidation.error("Workspace is required");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBox(@QueryParameter String box) {
            if (StringUtils.isBlank(box)) {
                return FormValidation.error("Box to deploy is required");
            }
            return FormValidation.ok();
        }

        public DescriptorHelper.JsonArrayResponse doGetBoxStack(
                @QueryParameter String cloud,
                @QueryParameter String workspace,
                @QueryParameter String box,
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(ClientCache.getClient(cloud), workspace, box,
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public DescriptorHelper.JsonArrayResponse doGetInstances(
                @QueryParameter String cloud,
                @QueryParameter String workspace,
                @QueryParameter String box,
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJsonArrayResponse(ClientCache.getClient(cloud),
                    workspace, StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public void validateSlaveConfiguration(ProjectSlaveConfiguration slaveConfig) throws FormException {

            ElasticBoxCloud cloud = slaveConfig.getElasticBoxCloud();

            if (cloud == null) {
                throw new FormException(
                    "Invalid ElasticBox cloud is selected for launching dedicated slave",
                    SLAVE_CONFIGURATION);
            }

            if (slaveConfig.getProfile() != null) {

                if (StringUtils.isBlank(slaveConfig.getProfile())) {
                    throw new FormException(
                        MessageFormat.format(
                            "No Deployment Policy is selected to launch dedicated slave in ElasticBox cloud ''{0}''.",
                            cloud.getDisplayName()),
                        SLAVE_CONFIGURATION);
                }

            } else if (slaveConfig.getClaims() != null) {

                if (StringUtils.isBlank(slaveConfig.getClaims())) {
                    throw new FormException(
                        MessageFormat.format(
                            "Claims must be specified to select a Deployment Policy to launch dedicated slave "
                                + "in ElasticBox cloud ''{0}''.",
                            cloud.getDisplayName()),
                        SLAVE_CONFIGURATION);
                }

            } else if (slaveConfig.getProvider() != null) {

                if (StringUtils.isBlank(slaveConfig.getProvider())) {
                    throw new FormException(
                        MessageFormat.format("No Provider is selected to launch dedicated slave in ElasticBox"
                            + " cloud ''{0}''.",
                            cloud.getDisplayName()),
                        SLAVE_CONFIGURATION);
                }

                if (StringUtils.isBlank(slaveConfig.getLocation())) {
                    throw new FormException(
                        MessageFormat.format("No Region is selected to launch dedicated slave in"
                            + " ElasticBox cloud ''{0}''.",
                            cloud.getDisplayName()),
                        SLAVE_CONFIGURATION);
                }

            } else {
                throw new FormException(
                    MessageFormat.format(
                        "No deployment option is selected to launch dedicated slave in ElasticBox cloud ''{0}''.",
                        cloud.getDisplayName()),
                    SLAVE_CONFIGURATION);
            }


            FormValidation result = checkBoxVersion(
                slaveConfig.getBoxVersion(),
                slaveConfig.getBox(),
                slaveConfig.getWorkspace(),
                ClientCache.getClient(slaveConfig.getCloud()));

            if (result.kind == FormValidation.Kind.ERROR) {
                throw new FormException(result.getMessage(), "boxVersion");
            }
        }

        private boolean anyOfThemIsBlank(String... inputParameters) {
            for (String inputParameter : inputParameters) {
                if (StringUtils.isBlank(inputParameter)) {
                    return true;
                }
            }
            return false;
        }


        private ListBoxModel getEmptyListBoxModel() {
            return getEmptyListBoxModel("","");
        }

        private ListBoxModel getEmptyListBoxModel(final String emptyName, final String emptyValue) {
            return new ListBoxModel(Arrays.asList(new ListBoxModel.Option(emptyName, emptyValue)));
        }

    }


}
