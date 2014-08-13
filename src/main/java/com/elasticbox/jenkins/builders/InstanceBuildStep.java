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

import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.DescriptorHelper;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


/**
 *
 * @author Phong Nguyen Le
 */
public abstract class InstanceBuildStep extends Builder {
    private String cloud;
    private final String workspace;
    private final String box;
    private final String instance;
    private final String buildStep;
    
    public InstanceBuildStep(String cloud, String workspace, String box, String instance, String buildStep) {
        this.cloud = cloud;
        this.workspace = workspace;
        this.box = box;
        this.instance = instance;
        this.buildStep = buildStep;
    }

    protected Object readResolve() {
        if (cloud == null) {
            ElasticBoxCloud ebCloud = ElasticBoxCloud.getInstance();
            if (ebCloud != null) {
                cloud = ebCloud.name;
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

    public String getInstance() {
        return instance;
    }    

    public String getBuildStep() {
        return buildStep;
    }
    
    protected IInstanceProvider getInstanceProvider(AbstractBuild build) {
        if (cloud != null) {
            return new IInstanceProvider() {

                public String getId() {
                    return null;
                }

                public String getInstanceId() {
                    return instance;
                }

                public ElasticBoxCloud getElasticBoxCloud() {
                    return (ElasticBoxCloud) Jenkins.getInstance().getCloud(cloud);
                }
                
            };
        }
        
        for (Object builder : ((Project) build.getProject()).getBuilders()) {
            if (builder instanceof IInstanceProvider) {
                IInstanceProvider instanceProvider = (IInstanceProvider) builder;
                if (buildStep.equals(instanceProvider.getId())) {
                    return instanceProvider;
                }
            }
        }
        
        return null;        
    }
    
    public static abstract class Descriptor extends BuildStepDescriptor<Builder> {
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
                formData.remove("cloud");
                formData.remove("workspace");
                formData.remove("box");
                formData.remove("instance");
            } else {
                formData.remove("buildStep");
            }
            
            return super.newInstance(req, formData);
        }               

        public ListBoxModel doFillCloudItems() {
            return DescriptorHelper.getClouds();
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {
            return DescriptorHelper.getWorkspaces(cloud);
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            ListBoxModel boxes = DescriptorHelper.getBoxes(cloud, workspace);
            boxes.add(0, new ListBoxModel.Option("Any Box", DescriptorHelper.ANY_BOX));
            return boxes;
        }
        
        public ListBoxModel doFillInstanceItems(@QueryParameter String cloud, @QueryParameter String workspace, 
                @QueryParameter String box) {                
            return DescriptorHelper.getInstances(cloud, workspace, box);
        }
        
        public ListBoxModel doFillBuildStepItems() {
            ListBoxModel buildSteps = new ListBoxModel();
            buildSteps.add("Loading...", "loading");
            return buildSteps;
        }

        public FormValidation doCheckCloud(@QueryParameter String value) {
            return DescriptorHelper.checkCloud(value);
        }
        
    }
    
}
