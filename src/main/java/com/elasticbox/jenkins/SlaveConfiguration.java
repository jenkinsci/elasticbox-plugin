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

import com.elasticbox.Client;
import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.model.services.deployment.execution.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Node;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class SlaveConfiguration extends AbstractSlaveConfiguration {

    private static final Logger LOGGER = Logger.getLogger(SlaveConfiguration.class.getName());

    public static final String SLAVE_CONFIGURATIONS = "slaveConfigurations";

    @DataBoundConstructor
    public SlaveConfiguration(String id, String workspace, String box, String boxVersion, String profile,
            String claims, String provider, String location, int minInstances, int maxInstances, String tags,
            String variables, String labels,String description, String remoteFs, Node.Mode mode, int retentionTime,
            String maxBuildsText, int executors, int launchTimeout, String boxDeploymentType) {

        super(id, workspace, box, boxVersion, profile, claims, provider, location, minInstances, maxInstances,
                tags, variables, labels, description, remoteFs, mode, retentionTime,
                StringUtils.isBlank(maxBuildsText) ? 0 : Integer.parseInt(maxBuildsText), executors, launchTimeout,
                boxDeploymentType);
    }

    @Extension
    public static final class DescriptorImpl extends AbstractSlaveConfigurationDescriptor {

        @Override
        public String getDisplayName() {
            return "Slave Configuration";
        }

        public void validateSlaveConfiguration(SlaveConfiguration slaveConfig, ElasticBoxCloud newCloud)
            throws FormException {

            String slaveConfigText = slaveConfig.getDescription() != null
                ? MessageFormat.format("slave configuration ''{0}''", slaveConfig.getDescription())
                : "a slave configuration";

            if (StringUtils.isBlank(slaveConfig.getWorkspace())) {
                throw new FormException(
                    MessageFormat.format(
                        "No Workspace is selected for {0} of ElasticBox cloud ''{1}''.",
                        slaveConfigText,
                        newCloud.getDisplayName()),
                    SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (StringUtils.isBlank(slaveConfig.getBox())) {
                throw new FormException(
                    MessageFormat.format(
                        "No Box is selected for {0} of ElasticBox cloud ''{1}''.",
                        slaveConfigText,
                        newCloud.getDisplayName()),
                    SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (StringUtils.isBlank(slaveConfig.getBoxVersion())) {
                throw new FormException(
                    MessageFormat.format(
                        "No Version is selected for the selected box in {0} of ElasticBox cloud ''{1}''.",
                        slaveConfigText,
                        newCloud.getDisplayName()),
                    SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (slaveConfig.getProfile() != null) {
                if (StringUtils.isBlank(slaveConfig.getProfile())) {
                    throw new FormException(
                        MessageFormat.format(
                            "No Deployment Policy is selected for {0} of ElasticBox cloud ''{1}''.",
                            slaveConfigText,
                            newCloud.getDisplayName()),
                        SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
            } else if (slaveConfig.getClaims() != null) {
                if (StringUtils.isBlank(slaveConfig.getClaims())) {
                    throw new FormException(
                        MessageFormat.format(
                            "Claims must be specified to select a Deployment Policy for {0} "
                                + "of ElasticBox cloud ''{1}''.",
                            slaveConfigText,
                            newCloud.getDisplayName()),
                        SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
            } else if (slaveConfig.getProvider() != null) {
                if (StringUtils.isBlank(slaveConfig.getProvider())) {
                    throw new FormException(
                        MessageFormat.format(
                            "No Provider is selected for {0} of ElasticBox cloud ''{1}''.",
                            slaveConfigText,
                            newCloud.getDisplayName()),
                        SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
                if (StringUtils.isBlank(slaveConfig.getLocation())) {
                    throw new FormException(
                        MessageFormat.format(
                            "No Region is selected for {0} of ElasticBox cloud ''{1}''.",
                            slaveConfigText,
                            newCloud.getDisplayName()),
                        SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
            } else {
                throw new FormException(
                    MessageFormat.format(
                        "No deployment option is selected for {0} of ElasticBox cloud ''{1}''.",
                        slaveConfigText,
                        newCloud.getDisplayName()),
                    SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (slaveConfig.getExecutors() < 1) {
                slaveConfig.setExecutors(1);
            }

            if (StringUtils.isBlank(slaveConfig.getId())) {
                slaveConfig.setId(UUID.randomUUID().toString());
            }

            FormValidation result = ((SlaveConfiguration.DescriptorImpl)Jenkins.get()
                .getDescriptorOrDie(SlaveConfiguration.class))
                .doCheckBoxVersion(slaveConfig.getBoxVersion(),
                        newCloud.name,
                        newCloud.getEndpointUrl(),
                        newCloud.getToken(),
                        slaveConfig.getWorkspace(),
                        slaveConfig.getBox());

            if (result.kind == FormValidation.Kind.ERROR) {
                throw new FormException(result.getMessage(), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }
        }

        private Client retrieveClient(String name, String endpointUrl, String token) {
            if (StringUtils.isBlank(endpointUrl) || StringUtils.isBlank(token)) {
                return null;
            }

            Client client = (name != null && name.length() > 0)
                    ? ClientCache.getClient(name)
                    : new Client(endpointUrl, token, ClientCache.getJenkinsHttpProxyCfg());

            if ( !client.getEndpointUrl().equals(endpointUrl) ) {
                client = new Client(endpointUrl, token, ClientCache.getJenkinsHttpProxyCfg());
            }

            return client;
        }

        public ListBoxModel doFillWorkspaceItems(@RelativePath("..") @QueryParameter String name,
                                                 @RelativePath("..") @QueryParameter String endpointUrl,
                                                 @RelativePath("..") @QueryParameter String token) {

            final ListBoxModel workspaceOptions = getEmptyListBoxModel(Constants.CHOOSE_WORKSPACE_MESSAGE, "");
            if (anyOfThemIsBlank(endpointUrl, token)) {
                return workspaceOptions;
            }

            try {
                final DeployBoxOrderResult<List<AbstractWorkspace>> result =
                        new DeployBoxOrderServiceImpl(retrieveClient(name, endpointUrl, token)).getWorkspaces();

                final List<AbstractWorkspace> workspaces = result.getResult();
                for (AbstractWorkspace workspace : workspaces) {
                    workspaceOptions.add(workspace.getName(), workspace.getId());
                }
            } catch (ServiceException e) {
                LOGGER.warning("Invalid parameters: endpointUrl=" + endpointUrl + ", token=" + token);
            }

            return workspaceOptions;
        }

        public ListBoxModel doFillBoxItems(@RelativePath("..") @QueryParameter String name,
                                           @RelativePath("..") @QueryParameter String endpointUrl,
                                           @RelativePath("..") @QueryParameter String token,
                                           @QueryParameter String workspace) {

            ListBoxModel boxes = getEmptyListBoxModel(Constants.CHOOSE_BOX_MESSAGE, "");
            if (anyOfThemIsBlank(token, workspace)) {
                return boxes;
            }

            try {
                final DeployBoxOrderResult<List<AbstractBox>> result =
                    new DeployBoxOrderServiceImpl(retrieveClient(name, endpointUrl, token)).updateableBoxes(workspace);

                for (AbstractBox box : result.getResult()) {
                    boxes.add(box.getName(), box.getId());
                }
            } catch (ServiceException e) {
                LOGGER.warning("Invalid parameters: endpointUrl=" + endpointUrl + ", token=" + token);
            }

            return boxes;
        }

        public ListBoxModel doFillBoxDeploymentTypeItems(@RelativePath("..") @QueryParameter String name,
                                                         @RelativePath("..") @QueryParameter String endpointUrl,
                                                         @RelativePath("..") @QueryParameter String token,
                                                         @QueryParameter String workspace,
                                                         @QueryParameter String box) {

            ListBoxModel boxDeploymentType = getEmptyListBoxModel(Constants.CHOOSE_DEPLOYMENT_TYPE_MESSAGE, "");
            if (anyOfThemIsBlank(endpointUrl, token, workspace, box)) {
                return boxDeploymentType;
            }

            try {
                final DeploymentType deploymentType
                    = new DeployBoxOrderServiceImpl(retrieveClient(name, endpointUrl, token)).deploymentType(box);

                final String id = deploymentType.getValue();
                boxDeploymentType.add(new ListBoxModel.Option(id,id,true));
            } catch (ServiceException e) {
                LOGGER.warning("Invalid parameters: endpointUrl=" + endpointUrl + ", token=" + token);
            }

            return boxDeploymentType;
        }

        public ListBoxModel doFillBoxVersionItems(@RelativePath("..") @QueryParameter String name,
                                                  @RelativePath("..") @QueryParameter String endpointUrl,
                                                  @RelativePath("..") @QueryParameter String token,
                                                  @QueryParameter String workspace,
                                                  @QueryParameter String box) {

            if (anyOfThemIsBlank(endpointUrl, workspace, box, token)) {
                return getEmptyListBoxModel();
            }

            return DescriptorHelper.getBoxVersions(retrieveClient(name, endpointUrl, token), workspace, box);
        }

        public ListBoxModel doFillProfileItems(@RelativePath("..") @QueryParameter String name,
                                               @RelativePath("..") @QueryParameter String endpointUrl,
                                                @RelativePath("..") @QueryParameter String token,
                                                @QueryParameter String workspace,
                                                @QueryParameter String box) {

            if (anyOfThemIsBlank(endpointUrl, workspace, box, token)) {
                return getEmptyListBoxModel();
            }

            List<PolicyBox> policyBoxList = null;
            try {
                DeployBoxOrderResult<List<PolicyBox>> result = new DeployBoxOrderServiceImpl(
                        retrieveClient(name, endpointUrl, token) ).deploymentPolicies(workspace, box);

                policyBoxList = result.getResult();

            } catch (ServiceException e) {
                LOGGER.severe("Invalid parameters: endpointUrl=" + endpointUrl + ", token=" + token);
            }

            final ListBoxModel profiles;
            if (policyBoxList == null) {
                profiles = getEmptyListBoxModel("--Unable to retrieve policies--", "");
            } else {
                if (policyBoxList.size() == 0) {
                    profiles = getEmptyListBoxModel("--No compatible policy box found--", "");
                } else if (policyBoxList.size() == 1) {
                    profiles = new ListBoxModel();
                } else {
                    profiles = getEmptyListBoxModel("--Please choose policy box--", "");
                }

                for (PolicyBox policyBox : policyBoxList) {
                    profiles.add(policyBox.getName(), policyBox.getId());
                }
            }

            if (profiles.size() == 1 && !"".equals(profiles.get(0).name)) {
                profiles.get(0).selected = true;
            }
            return  profiles;
        }

        public ListBoxModel doFillProviderItems(@RelativePath("..") @QueryParameter String name,
                                                @RelativePath("..") @QueryParameter String endpointUrl,
                                                @RelativePath("..") @QueryParameter String token,
                                                @QueryParameter String workspace) {
            ListBoxModel providers = getEmptyListBoxModel(Constants.CHOOSE_PROVIDER_MESSAGE, "");

            if (anyOfThemIsBlank(endpointUrl, token, workspace)) {
                return providers;
            }

            return DescriptorHelper.getCloudFormationProviders(retrieveClient(name, endpointUrl, token), workspace);
        }

        public ListBoxModel doFillLocationItems(@RelativePath("..") @QueryParameter String name,
                                                @RelativePath("..") @QueryParameter String endpointUrl,
                                                @RelativePath("..") @QueryParameter String token,
                                                @QueryParameter String provider) {
            ListBoxModel locations = getEmptyListBoxModel(Constants.CHOOSE_REGION_MESSAGE, "");
            if (anyOfThemIsBlank(endpointUrl, token, provider)) {
                return locations;
            }
            return DescriptorHelper.getCloudFormationLocations(retrieveClient(name, endpointUrl, token), provider);
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

        public FormValidation doCheckBoxVersion(@QueryParameter String value,
                                                @RelativePath("..") @QueryParameter String name,
                                                @RelativePath("..") @QueryParameter String endpointUrl,
                                                @RelativePath("..") @QueryParameter String token,
                                                @QueryParameter String workspace,
                                                @QueryParameter String box) {
            if (StringUtils.isBlank(workspace)) {
                return FormValidation.error("Workspace is required");
            }
            if (StringUtils.isBlank(box)) {
                return FormValidation.error("Box is required");
            }

            return checkBoxVersion(value, box, workspace, retrieveClient(name, endpointUrl, token));
        }

        public DescriptorHelper.JsonArrayResponse doGetBoxStack(@RelativePath("..") @QueryParameter String name,
                                                                @RelativePath("..") @QueryParameter String endpointUrl,
                                                                @RelativePath("..") @QueryParameter String token,
                                                                @QueryParameter String workspace,
                                                                @QueryParameter String box,
                                                                @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(retrieveClient(name, endpointUrl, token), workspace, box,
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public DescriptorHelper.JsonArrayResponse doGetInstances(@RelativePath("..") @QueryParameter String name,
                                                                 @RelativePath("..") @QueryParameter String endpointUrl,
                                                                 @RelativePath("..") @QueryParameter String token,
                                                                 @QueryParameter String workspace,
                                                                 @QueryParameter String box,
                                                                 @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJsonArrayResponse(retrieveClient(name, endpointUrl, token),
                    workspace, StringUtils.isBlank(boxVersion) ? box : boxVersion);
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

            final List<ListBoxModel.Option> options = Arrays.asList(new ListBoxModel.Option(emptyName, emptyValue));
            return new ListBoxModel(options);
        }


    }

}
