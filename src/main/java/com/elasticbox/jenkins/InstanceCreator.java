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

import com.elasticbox.jenkins.util.ClientCache;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class InstanceCreator extends BuildWrapper {

    private String cloud;
    private final String workspace;
    private final String box;
    private final String profile;
    private final String variables;
    private final String boxVersion;

    private transient ElasticBoxSlave ebSlave;

    
    @DataBoundConstructor
    public InstanceCreator(String cloud, String workspace, String box, String boxVersion, String profile, String variables) {
        super();
        this.cloud = cloud;
        this.workspace = workspace;
        this.box = box;
        this.boxVersion = boxVersion;
        this.profile = profile;
        this.variables = variables;        
    }
    
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {        
        for (Node node : build.getProject().getAssignedLabel().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getComputer().getBuilds().contains(build)) {
                    ebSlave = slave;
                    ebSlave.setInUse(true);
                    break;
                }
            }
        }

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                ebSlave.setInUse(false);
                if (ebSlave.isSingleUse()) {
                    ebSlave.getComputer().setAcceptingTasks(false);
                }                        
                build.getProject().setAssignedLabel(null);
                return true;
            }
        };
    }
    
    protected Object readResolve() {
        if (cloud == null) {
            ElasticBoxCloud ebCloud = ElasticBoxCloud.getInstance();
            if (ebCloud != null) {
                cloud = ebCloud.name;
                getDescriptor().save();
            }
        }
        
        return this;
    }

    public String getCloud() {
        return cloud;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }

    public String getBoxVersion() {
        return boxVersion;
    }
    
    public String getProfile() {
        return profile;
    }

    public String getVariables() {
        return variables;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox Instance Creation";
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            InstanceCreator instanceCreator = (InstanceCreator) super.newInstance(req, formData);
            FormValidation result = doCheckBoxVersion(instanceCreator.getBoxVersion(), instanceCreator.getCloud(), 
                    instanceCreator.getBox());
            if (result.kind == FormValidation.Kind.ERROR) {
                throw new FormException(result.getMessage(), "boxVersion");
            }
            
            return instanceCreator;
        }
        
        
        public ListBoxModel doFillCloudItems() {
            return DescriptorHelper.getClouds();
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {
            return DescriptorHelper.getWorkspaces(cloud);
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(cloud, workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@QueryParameter String cloud, @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(cloud, box);
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String cloud, @QueryParameter String workspace, 
                @QueryParameter String box) {                
            return DescriptorHelper.getProfiles(cloud, workspace, box);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(@QueryParameter String cloud, 
                @QueryParameter String box, @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(cloud, StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public DescriptorHelper.JSONArrayResponse doGetInstances(@QueryParameter String cloud, 
                @QueryParameter String workspace, @QueryParameter String box, @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJSONArrayResponse(cloud, workspace, 
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }
        
        public FormValidation doCheckBoxVersion(@QueryParameter String value, @QueryParameter String cloud, @QueryParameter String box) {
            return DescriptorHelper.checkSlaveBox(ClientCache.getClient(cloud), StringUtils.isBlank(value) ? box : value);
        }
    }
}
