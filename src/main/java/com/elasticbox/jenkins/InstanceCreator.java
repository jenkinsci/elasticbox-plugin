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

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.UUID;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class InstanceCreator extends BuildWrapper {
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
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {        
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
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
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
                Cloud c = Jenkins.getInstance().getCloud(cloud);
                if (c instanceof ElasticBoxCloud) {
                    ebCloud = (ElasticBoxCloud) c;
                }
            }
            slaveConfiguration = new ProjectSlaveConfiguration(UUID.randomUUID().toString(), cloud, workspace, box, 
                    boxVersion, profile, ebCloud != null ? ebCloud.getMaxInstances() : 1, null, variables, StringUtils.EMPTY, 
                    ebCloud != null ? ebCloud.getRetentionTime() : 30, 1, ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
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
            return "ElasticBox On-Demand Slave Creation";
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            InstanceCreator instanceCreator = (InstanceCreator) super.newInstance(req, formData);
            ProjectSlaveConfiguration.DescriptorImpl descriptor = (ProjectSlaveConfiguration.DescriptorImpl) instanceCreator.getSlaveConfiguration().getDescriptor();
            FormValidation result = descriptor.doCheckBoxVersion(instanceCreator.getSlaveConfiguration().getBoxVersion(), 
                    instanceCreator.getSlaveConfiguration().getCloud(), 
                    instanceCreator.getSlaveConfiguration().getBox());
            if (result.kind == FormValidation.Kind.ERROR) {
                throw new FormException(result.getMessage(), "boxVersion");
            }
            
            return instanceCreator;
        }        
        
    }
}
