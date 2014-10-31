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

import com.elasticbox.Client;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.TaskLogger;
import hudson.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


/**
 *
 * @author Phong Nguyen Le
 * @deprecated
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

                public String getInstanceId(AbstractBuild build) {
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
    
    static void waitForCompletion(String operationDisplayName, List<IProgressMonitor> monitors, ElasticBoxCloud ebCloud, 
            Client client, TaskLogger logger) throws IOException {
        Map<String, IProgressMonitor> instanceIdToMonitorMap = new HashMap<String, IProgressMonitor>();        
        for (IProgressMonitor monitor : monitors) {
            instanceIdToMonitorMap.put(Client.getResourceId(monitor.getResourceUrl()), monitor);
        }
        Object waitLock = new Object();
        long startWaitTime = System.currentTimeMillis();
        while (!instanceIdToMonitorMap.isEmpty() && TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startWaitTime) < ElasticBoxSlaveHandler.TIMEOUT_MINUTES) {
            synchronized(waitLock) {
                try {
                    waitLock.wait(3000);
                } catch (InterruptedException ex) {
                }
            }                        
            List<String> instanceIDs = new ArrayList<String>(instanceIdToMonitorMap.keySet());
            JSONArray instances = client.getInstances(instanceIDs);
            for (Object instance : instances) {
                JSONObject instanceJson = (JSONObject) instance;
                String instanceId = instanceJson.getString("id");
                instanceIDs.remove(instanceId);
                IProgressMonitor monitor = instanceIdToMonitorMap.get(instanceId);
                String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), instanceJson);
                boolean done;
                try {
                    done = monitor.isDone(instanceJson);
                } catch (IProgressMonitor.IncompleteException ex) {
                    logger.error("Failed to perform operation {0} for instance {0}: {1}", operationDisplayName, instancePageUrl, ex.getMessage());
                    throw new AbortException(ex.getMessage());
                }
                
                if (done) {
                    logger.info(MessageFormat.format("Operation {0} is successful for instance {1}", operationDisplayName, instancePageUrl));                    
                    instanceIdToMonitorMap.remove(instanceId);
                }
            }
            
            if (!instanceIDs.isEmpty()) {
                throw new AbortException(MessageFormat.format("Cannot find the instances with the following IDs: {0}", StringUtils.join(instanceIDs, ", ")));
            }            
        }
        
        if (!instanceIdToMonitorMap.isEmpty()) {
            List<String> instancePageURLs = new ArrayList<String>();
            for (IProgressMonitor monitor : instanceIdToMonitorMap.values()) {
                instancePageURLs.add(Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl()));
            }
            String message = MessageFormat.format("The following instances still are not ready after waiting for {0} minutes: {1}", 
                    TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startWaitTime), StringUtils.join(instancePageURLs, ','));
            logger.error(message);
            throw new AbortException(message);
        }        
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
