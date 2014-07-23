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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


/**
 *
 * @author Phong Nguyen Le
 */
public abstract class InstanceBuildStep extends Builder {
    private final String instance;
    private final String workspace;
    private final String buildStep;
    private String box;
    
    public InstanceBuildStep(String workspace, String box, String instance, String buildStep) {
        this.workspace = workspace;
        this.box = box;
        this.instance = instance;
        this.buildStep = buildStep;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }        

    public String getInstance() {
        return instance;
    }    

    public String getBuildStep() {
        return buildStep;
    }
    
    protected String getInstanceId(AbstractBuild build) {
        if (instance != null && !instance.isEmpty()) {
            return instance;
        }
        
        for (Object builder : ((Project) build.getProject()).getBuilders()) {
            if (builder instanceof IInstanceProvider) {
                IInstanceProvider instanceProvider = (IInstanceProvider) builder;
                if (buildStep.equals(instanceProvider.getId())) {
                    return instanceProvider.getInstanceId();
                }
            }
        }
        
        return null;
    }
        
    public static abstract class Descriptor extends BuildStepDescriptor<Builder> {
        protected final ElasticBoxItemProvider itemProvider = new ElasticBoxItemProvider();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
        
        protected boolean isPreviousBuildStepSelected(JSONObject formData) {
            return formData.containsKey("instanceType") && "eb-instance-from-prior-buildstep".equals(formData.getString("instanceType"));
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (isPreviousBuildStepSelected(formData)) {
                formData.remove("workspace");
                formData.remove("box");
                formData.remove("instance");
            } else {
                formData.remove("buildStep");
            }
            
            return super.newInstance(req, formData);
        }               

        public ListBoxModel doFillWorkspaceItems() {
            return itemProvider.getWorkspaces();
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String workspace) {
            ListBoxModel boxes = itemProvider.getBoxes(workspace);
            boxes.add(0, new ListBoxModel.Option("Any Box", "AnyBox"));
            return boxes;
        }
        
        public ListBoxModel doFillInstanceItems(@QueryParameter String workspace, @QueryParameter String box) {                
            ListBoxModel instances = itemProvider.getInstances(workspace, box);
            return instances;
        }
        
        public ListBoxModel doFillBuildStepItems() {
            ListBoxModel buildSteps = new ListBoxModel();
            buildSteps.add("Loading...", "loading");
            return buildSteps;
        }
        
    }
    
}
