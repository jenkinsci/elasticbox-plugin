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

import com.elasticbox.jenkins.ElasticBoxItemProvider;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class InstanceBuildStep extends Builder {
    protected final String instance;
    protected final String workspace;
    private final String buildStep;
    
    public InstanceBuildStep(String workspace, String instance, String buildStep) {
        this.workspace = workspace;
        this.instance = instance;
        this.buildStep = buildStep;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getInstance() {
        return instance;
    }    

    public String getBuildStep() {
        return buildStep;
    }
    
    public static abstract class Descriptor extends BuildStepDescriptor<Builder> {
        protected final ElasticBoxItemProvider itemProvider = new ElasticBoxItemProvider();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public ListBoxModel doFillWorkspaceItems() {
            return itemProvider.getWorkspaces();
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String workspace) {
            ListBoxModel boxes = itemProvider.getBoxes(workspace);
            boxes.add(0, new ListBoxModel.Option("Any Box", ""));
            return boxes;
        }
        
        public ListBoxModel doFillInstanceItems(@QueryParameter String workspace, @QueryParameter String box) {                
            ListBoxModel instances = itemProvider.getInstances(workspace, box);
            return instances;
        }
        
    }
    
}
