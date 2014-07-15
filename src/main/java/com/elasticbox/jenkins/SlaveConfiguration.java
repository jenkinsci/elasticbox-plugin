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
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.ListBoxModel;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveConfiguration implements Describable<SlaveConfiguration> {

    private final String workspace;
    private final String box;
    private final String profile;
    private final String variables;
    private final String boxVersion;
    private final int maxInstances;
    private final String environment;
    private final String labels;
    private final String remoteFS;
    private final String description;
    private final Node.Mode mode;
    private final int idleTerminationTime;
    private final int executors;
    private final int launchTimeout;
    
    private transient Set<LabelAtom> labelSet;

    @DataBoundConstructor
    public SlaveConfiguration(String workspace, String box, String boxVersion, String profile, int maxInstances, 
            String environment, String variables, String labels, String description, String remoteFS, Node.Mode mode, 
            int idleTerminationTime, int executors, int launchTimeout) {
        super();
        this.workspace = workspace;
        this.box = box;
        this.boxVersion = boxVersion;
        this.profile = profile;
        this.maxInstances = maxInstances;
        this.environment = environment;
        this.variables = variables;    
        this.labels = labels;
        this.description = description;
        this.remoteFS = remoteFS;
        this.mode = mode;
        this.idleTerminationTime = idleTerminationTime;
        this.executors = executors;
        this.launchTimeout = launchTimeout;
        
        this.labelSet = getLabelSet();
    }    
    
    public Descriptor<SlaveConfiguration> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }
    
    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }

    public String getProfile() {
        return profile;
    }

    public String getVariables() {
        return variables;
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getLabels() {
        return labels;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public String getDescription() {
        return description;
    }

    public int getExecutors() {
        return executors;
    }

    public int getIdleTerminationTime() {
        return idleTerminationTime;
    }

    public int getLaunchTimeout() {
        return launchTimeout;
    }

    public Node.Mode getMode() {
        return mode;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public Set<LabelAtom> getLabelSet() {
        if (labelSet == null) {
            labelSet = Label.parse(labels);
        }
        
        return labelSet;
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<SlaveConfiguration> {
        private final ElasticBoxItemProvider itemProvider = new ElasticBoxItemProvider();

        @Override
        public String getDisplayName() {
            return null;
        }

        public ListBoxModel doFillWorkspaceItems() {
            return itemProvider.getWorkspaces();
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String workspace) {
            return itemProvider.getBoxes(workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@QueryParameter String box) {
            return itemProvider.getBoxVersions(box);
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String workspace, @QueryParameter String box) {                
            ListBoxModel instances = itemProvider.getProfiles(workspace, box);
            
            return instances;
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetBoxStack(@QueryParameter String boxVersion, @QueryParameter String box) {
            return StringUtils.isBlank(boxVersion) ? itemProvider.getBoxStack(box) : itemProvider.getBoxStack(boxVersion);
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetInstances(@QueryParameter String workspace, @QueryParameter String boxVersion) {
            return itemProvider.getInstancesAsJSONArrayResponse(workspace, boxVersion);
        }
        
    }

}
