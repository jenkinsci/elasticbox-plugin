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
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class ProjectSlaveConfiguration extends AbstractSlaveConfiguration {

    private final String cloud;

    @DataBoundConstructor
    public ProjectSlaveConfiguration(String id, String cloud, String workspace, String box, String boxVersion, 
            String profile, int maxInstances, String environment, String variables, String remoteFS, int retentionTime, 
            int executors, int launchTimeout) {
        super(StringUtils.isBlank(id) ? UUID.randomUUID().toString() : id, workspace, box, boxVersion, profile, 0, 
                maxInstances, environment, variables, null, StringUtils.EMPTY, remoteFS, Node.Mode.EXCLUSIVE, retentionTime, 
                executors, launchTimeout);
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
    public static final class DescriptorImpl extends Descriptor<AbstractSlaveConfiguration> {

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

        public ListBoxModel doFillBoxVersionItems(@QueryParameter String cloud, @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(ClientCache.getClient(cloud), box);
        }
        
        public FormValidation doCheckBoxVersion(@QueryParameter String value, @QueryParameter String cloud, 
                @QueryParameter String box) {
            return DescriptorHelper.checkSlaveBox(ClientCache.getClient(cloud), 
                    StringUtils.isBlank(value) ? box : value);
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String cloud, @QueryParameter String workspace, 
                @QueryParameter String box) {                
            return DescriptorHelper.getProfiles(ClientCache.getClient(cloud), workspace, box);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(
                @QueryParameter String cloud, 
                @QueryParameter String box, 
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(ClientCache.getClient(cloud),
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
        
    }
    
    
}
