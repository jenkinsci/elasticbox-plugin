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

import static com.elasticbox.jenkins.DescriptorHelper.anyOfThemIsBlank;
import static com.elasticbox.jenkins.DescriptorHelper.getEmptyListBoxModel;
import static com.elasticbox.jenkins.DescriptorHelper.getEmptyListBoxModel;

import com.elasticbox.Client;
import com.elasticbox.jenkins.migration.AbstractConverter;
import com.elasticbox.jenkins.migration.RetentionTimeConverter;
import com.elasticbox.jenkins.migration.Version;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.util.ClientCache;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.XStream2;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


public class InstanceCreator extends BuildWrapper {

    private static final Logger logger = Logger.getLogger(InstanceCreator.class.getName());

    @Deprecated
    private String cloud;
    @Deprecated
    private String workspace;
    @Deprecated
    private String box;
    @Deprecated
    private String profile;
    @Deprecated
    private String variables;
    @Deprecated
    private String boxVersion;

    private ProjectSlaveConfiguration slaveConfiguration;

    private transient ElasticBoxSlave ebSlave;

    @DataBoundConstructor
    public InstanceCreator(ProjectSlaveConfiguration slaveConfiguration) {
        super();
        this.slaveConfiguration = slaveConfiguration;
    }

    public ProjectSlaveConfiguration getSlaveConfiguration() {
        return slaveConfiguration;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
        throws IOException, InterruptedException {

        for (Node node : build.getProject().getAssignedLabel().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getComputer().getBuilds().contains(build)) {
                    ebSlave = slave;
                    break;
                }
            }
        }

        return new Environment() {

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                throws IOException, InterruptedException {

                if (ebSlave.isSingleUse()) {
                    ebSlave.getComputer().setAcceptingTasks(false);
                }
                build.getProject().setAssignedLabel(null);
                return true;
            }
        };
    }

    protected Object readResolve() {
        if (slaveConfiguration == null) {
            ElasticBoxCloud ebCloud = null;
            if (cloud == null) {
                ebCloud = ElasticBoxCloud.getInstance();
                if (ebCloud != null) {
                    cloud = ebCloud.name;
                }
            } else {
                Cloud cloud = Jenkins.get().getCloud(this.cloud);
                if (cloud instanceof ElasticBoxCloud) {
                    ebCloud = (ElasticBoxCloud) cloud;
                }
            }
            slaveConfiguration = new ProjectSlaveConfiguration(
                UUID.randomUUID().toString(),
                cloud,
                workspace,
                box,
                boxVersion,
                profile,
                null,
                null,
                null,
                ebCloud != null
                    ? ebCloud.getMaxInstances()
                    : 1, null, variables,
                StringUtils.EMPTY, 30, null, 1,
                ElasticBoxSlaveHandler.TIMEOUT_MINUTES, null);
        }

        return this;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Launch dedicated slave via ElasticBox";
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {

            JSONObject slaveConfigJson = formData.getJSONObject(ProjectSlaveConfiguration.SLAVE_CONFIGURATION);

            if (anyOfThemIsBlank(
                slaveConfigJson.getString("cloud"),
                slaveConfigJson.getString("workspace"),
                slaveConfigJson.getString("box"))) {

                throw new FormException(
                    "Required fields should be provided",
                    ProjectSlaveConfiguration.SLAVE_CONFIGURATION);
            }

            DescriptorHelper.fixDeploymentPolicyFormData(slaveConfigJson);

            InstanceCreator instanceCreator = (InstanceCreator) super.newInstance(req, formData);

            ProjectSlaveConfiguration.DescriptorImpl descriptor
                = (ProjectSlaveConfiguration.DescriptorImpl) instanceCreator.getSlaveConfiguration().getDescriptor();

            descriptor.validateSlaveConfiguration(instanceCreator.getSlaveConfiguration());

            return instanceCreator;
        }

    }

    public static class ConverterImpl extends AbstractConverter<InstanceCreator> {

        public ConverterImpl(XStream2 xstream) {
            super(xstream, Arrays.asList(new DeploymentTypeMigrator(), new RetentionTimeMigrator()));
        }
    }

    private static class DeploymentTypeMigrator extends AbstractConverter.Migrator<InstanceCreator> {

        public DeploymentTypeMigrator() {
            super(Version._4_0_3);
        }

        @Override
        protected void migrate(InstanceCreator instanceCreator, Version olderVersion) {

            final ProjectSlaveConfiguration slaveConfiguration = instanceCreator.getSlaveConfiguration();

            if (StringUtils.isBlank(slaveConfiguration.getBoxDeploymentType())) {

                if (StringUtils.isNotBlank(slaveConfiguration.getCloud())) {

                    final Client client = ClientCache.getClient(slaveConfiguration.getCloud());

                    final DeploymentType deploymentType
                        = new DeployBoxOrderServiceImpl(client).deploymentType(slaveConfiguration.getBox());

                    slaveConfiguration.boxDeploymentType = deploymentType.getValue();

                } else {
                    logger.log(
                        Level.SEVERE,
                        "InstanceCreator migration failed, there is no cloud configured to deploy: "
                            + slaveConfiguration.getBox());

                    throw new ServiceException(
                        "InstanceCreator migration failed, there is no cloud configured to deploy: "
                            + slaveConfiguration.getBox());
                }
            }
        }
    }

    private static class RetentionTimeMigrator extends AbstractConverter.Migrator<InstanceCreator> {

        public RetentionTimeMigrator() {
            super(Version._0_9_3);
        }

        @Override
        protected void migrate(InstanceCreator obj, Version olderVersion) {
            ProjectSlaveConfiguration slaveConfig = obj.getSlaveConfiguration();
            if (slaveConfig != null && slaveConfig.getRetentionTime() == 0) {
                slaveConfig.retentionTime = Integer.MAX_VALUE;
            }
        }
    }
}
