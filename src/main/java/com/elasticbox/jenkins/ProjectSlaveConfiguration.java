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

import com.elasticbox.jenkins.model.box.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.api.BoxRepositoryAPIImpl;
import com.elasticbox.jenkins.model.services.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.Extension;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class ProjectSlaveConfiguration extends AbstractSlaveConfiguration {

    private static final Logger LOGGER = Logger.getLogger(ProjectSlaveConfiguration.class.getName());

    static final String SLAVE_CONFIGURATION = "slaveConfiguration";

    private final String cloud;

    @DataBoundConstructor
    public ProjectSlaveConfiguration(String id, String cloud, String workspace, String box, String boxVersion,
            String profile, String claims, String provider, String location, int maxInstances, String tags,
            String variables, String remoteFS, int retentionTime, String maxBuildsText, int executors, int launchTimeout) {
        super(StringUtils.isBlank(id) ? UUID.randomUUID().toString() : id, workspace, box, boxVersion, profile,
                claims, provider, location, 0,
                maxInstances, tags, variables, null, StringUtils.EMPTY, remoteFS, Node.Mode.EXCLUSIVE,
                retentionTime, StringUtils.isBlank(maxBuildsText) ? 0 : Integer.parseInt(maxBuildsText), executors,
                launchTimeout);
        this.cloud = cloud;
    }

    public String getCloud() {
        return cloud;
    }

    public ElasticBoxCloud getElasticBoxCloud() {
        Cloud c = Jenkins.getInstance().getCloud(cloud);
        return (c instanceof ElasticBoxCloud ? (ElasticBoxCloud) c : null);
    }

    public static List<ProjectSlaveConfiguration> list() {
        List<ProjectSlaveConfiguration> slaveConfigurations = new ArrayList<ProjectSlaveConfiguration>();
        List<BuildableItemWithBuildWrappers> projects = Jenkins.getInstance().getItems(BuildableItemWithBuildWrappers.class);
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
            return DescriptorHelper.getClouds();
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {
            return DescriptorHelper.getWorkspaces(ClientCache.getClient(cloud));
        }

        public ListBoxModel doFillBoxItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(ClientCache.getClient(cloud), workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@QueryParameter String cloud, @QueryParameter String workspace,
                @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(ClientCache.getClient(cloud), workspace, box);
        }

        public FormValidation doCheckBoxVersion(@QueryParameter String value, @QueryParameter String cloud,
                @QueryParameter String workspace, @QueryParameter String box) {
            return checkBoxVersion(value, box, workspace, ClientCache.getClient(cloud));
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String cloud, @QueryParameter String workspace, @QueryParameter String box) {

            LOGGER.log(Level.FINE, "doFill ProfileItems - cloud: "+cloud+", worksapce: "+workspace+", box: "+box);

            ListBoxModel profiles = new ListBoxModel();

                if (StringUtils.isEmpty(cloud) || StringUtils.isEmpty(workspace) || StringUtils.isEmpty(box))
                    return profiles;

            try {

                final BoxRepository boxRepository = new BoxRepositoryAPIImpl((ClientCache.getClient(cloud)));

                final DeployBoxOrderResult<List<PolicyBox>> result = new DeployBoxOrderServiceImpl(boxRepository).deploymentPolicies(workspace, box);
                final List<PolicyBox> policyBoxList = result.getResult();
                for (PolicyBox policyBox : policyBoxList) {
                    profiles.add(policyBox.getName(), policyBox.getId());
                }

            } catch (ServiceException e) {
                LOGGER.log(Level.SEVERE, "ERROR doFillProfileItems - cloud: "+cloud+", worksapce: "+workspace+", box: "+box+" return an empty list");
                e.printStackTrace();
            }
            return  profiles;
        }

        public ListBoxModel doFillProviderItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            return DescriptorHelper.getCloudFormationProviders(ClientCache.getClient(cloud), workspace);
        }

        public ListBoxModel doFillLocationItems(@QueryParameter String cloud, @QueryParameter String provider) {
            return DescriptorHelper.getCloudFormationLocations(ClientCache.getClient(cloud), provider);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(
                @QueryParameter String cloud,
                @QueryParameter String workspace,
                @QueryParameter String box,
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(ClientCache.getClient(cloud), workspace, box,
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public DescriptorHelper.JSONArrayResponse doGetInstances(
                @QueryParameter String cloud,
                @QueryParameter String workspace,
                @QueryParameter String box,
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJSONArrayResponse(ClientCache.getClient(cloud),
                    workspace, StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public void validateSlaveConfiguration(ProjectSlaveConfiguration slaveConfig) throws FormException {
            ElasticBoxCloud cloud = slaveConfig.getElasticBoxCloud();
            if (cloud == null) {
                throw new FormException("Invalid ElasticBox cloud is selected for launching dedicated slave", SLAVE_CONFIGURATION);
            }
            if (slaveConfig.getProfile() != null) {
                if (StringUtils.isBlank(slaveConfig.getProfile())) {
                    throw new FormException(MessageFormat.format("No Deployment Policy is selected to launch dedicated slave in ElasticBox cloud ''{0}''.", cloud.getDisplayName()), SLAVE_CONFIGURATION);
                }
            } else if (slaveConfig.getClaims() != null) {
                if (StringUtils.isBlank(slaveConfig.getClaims())) {
                    throw new FormException(MessageFormat.format("Claims must be specified to select a Deployment Policy to launch dedicated slave in ElasticBox cloud ''{0}''.", cloud.getDisplayName()), SLAVE_CONFIGURATION);
                }
            } else if (slaveConfig.getProvider() != null) {
                if (StringUtils.isBlank(slaveConfig.getProvider())) {
                    throw new FormException(MessageFormat.format("No Provider is selected to launch dedicated slave in ElasticBox cloud ''{0}''.", cloud.getDisplayName()), SLAVE_CONFIGURATION);
                }
                if (StringUtils.isBlank(slaveConfig.getLocation())) {
                    throw new FormException(MessageFormat.format("No Region is selected to launch dedicated slave in ElasticBox cloud ''{0}''.", cloud.getDisplayName()), SLAVE_CONFIGURATION);
                }
            } else {
                throw new FormException(MessageFormat.format("No deployment option is selected to launch dedicated slave in ElasticBox cloud ''{0}''.", cloud.getDisplayName()), SLAVE_CONFIGURATION);
            }


            FormValidation result = doCheckBoxVersion(slaveConfig.getBoxVersion(), slaveConfig.getCloud(),
                    slaveConfig.getWorkspace(), slaveConfig.getBox());
            if (result.kind == FormValidation.Kind.ERROR) {
                throw new FormException(result.getMessage(), "boxVersion");
            }
        }

    }


}
