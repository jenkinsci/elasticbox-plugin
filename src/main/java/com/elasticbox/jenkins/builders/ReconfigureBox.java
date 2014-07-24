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
import com.elasticbox.jenkins.ElasticBoxItemProvider;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class ReconfigureBox extends InstanceBuildStep implements IInstanceProvider {
    private final String id;
    private final String variables;
    private final String buildStepVariables;

    private transient String instanceId;
    private transient ElasticBoxCloud ebCloud;
    
    @DataBoundConstructor
    public ReconfigureBox(String id, String cloud, String workspace, String box, String instance, String variables, String buildStep, String buildStepVariables) {
        super(cloud, workspace, box, instance, buildStep);
        assert id != null && id.startsWith(getClass().getName() + '-');
        this.id = id;
        this.variables = variables;
        this.buildStepVariables = buildStepVariables;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        IInstanceProvider instanceProvider = getInstanceProvider(build);
        if (instanceProvider == null || instanceProvider.getElasticBoxCloud() == null) {
            throw new IOException("No valid ElasticBox cloud is selected for this build step.");
        }
        
        ebCloud = instanceProvider.getElasticBoxCloud();
        VariableResolver resolver = new VariableResolver(build, listener);
        JSONArray jsonVariables = JSONArray.fromObject(getBuildStep() == null ? variables : buildStepVariables);
        for (Object variable : jsonVariables) {
            resolver.resolve((JSONObject) variable);
        }        
        
        IProgressMonitor monitor = ebCloud.createClient().reconfigure(
                instanceProvider.getInstanceId(), jsonVariables);
        String instancePageUrl = Client.getPageUrl(ebCloud.getEndpointUrl(), monitor.getResourceUrl());
        listener.getLogger().println(MessageFormat.format("Reconfiguring box instance {0}", instancePageUrl));
        listener.getLogger().println(MessageFormat.format("Waiting for the box instance {0} to be reconfigured", instancePageUrl));
        try {
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            listener.getLogger().println(MessageFormat.format("The box instance {0} has been reconfigured successfully ", instancePageUrl));
            instanceId = Client.getResourceId(monitor.getResourceUrl());
            return true;
        } catch (IProgressMonitor.IncompleteException ex) {
            Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, null, ex);
            listener.error("Failed to reconfigure box instance %s: %s", instancePageUrl, ex.getMessage());
            throw new AbortException(ex.getMessage());
        }
    }    

    public String getId() {
        return id;
    }

    public String getVariables() {
        return variables;
    }

    public String getBuildStepVariables() {
        return buildStepVariables;
    }
    
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public ElasticBoxCloud getElasticBoxCloud() {
        return ebCloud;
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor {
        @Override
        public String getDisplayName() {
            return "ElasticBox - Reconfigure Box";
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (isPreviousBuildStepSelected(formData)) {
                formData.remove("variables");
            } else {
                formData.remove("buildStepVariables");
            }

            return super.newInstance(req, formData);
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetVariables(@QueryParameter String cloud, 
                @QueryParameter String instance) {
            return ElasticBoxItemProvider.getInstanceVariables(cloud, instance);
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetBoxStack(@QueryParameter String cloud, 
                @QueryParameter String instance) {
            return ElasticBoxItemProvider.getInstanceBoxStack(cloud, instance);
        }
        
        public ElasticBoxItemProvider.JSONArrayResponse doGetInstances(@QueryParameter String cloud, 
                @QueryParameter String workspace, @QueryParameter String box) {
            return ElasticBoxItemProvider.getInstancesAsJSONArrayResponse(cloud, workspace, box);
        }
        
    }
}
