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
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class ReconfigureBox extends InstanceBuildStep {
    private final String variables;
    
    @DataBoundConstructor
    public ReconfigureBox(String workspace, String instance, String variables) {
        super(workspace, instance, null);
        this.variables = variables;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ElasticBoxCloud cloud = ElasticBoxCloud.getInstance();
        if (cloud == null) {
            throw new IOException("No ElasticBox cloud is configured.");
        }
        IProgressMonitor monitor = cloud.createClient().reconfigure(getInstanceId(build), JSONArray.fromObject(variables));
        String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), monitor.getResourceUrl());
        listener.getLogger().println(MessageFormat.format("Reconfiguring box instance {0}", instancePageUrl));
        listener.getLogger().println(MessageFormat.format("Waiting for the box instance {0} to be reconfigured", instancePageUrl));
        try {
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            listener.getLogger().println(MessageFormat.format("The box instance {0} has been reconfigured successfully ", instancePageUrl));
            return true;
        } catch (IProgressMonitor.IncompleteException ex) {
            Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, null, ex);
            listener.error("Failed to reconfigure box instance %s: %s", instancePageUrl, ex.getMessage());
            throw new AbortException(ex.getMessage());
        }
    }    

    public String getVariables() {
        return variables;
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor {
        @Override
        public String getDisplayName() {
            return "ElasticBox - Reconfigure Box";
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetVariables(@QueryParameter String instance) {
            return itemProvider.getInstanceVariables(instance);
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetBoxStack(@QueryParameter String instance) {
            return itemProvider.getInstanceBoxStack(instance);
        }
        
        public ElasticBoxItemProvider.JSONArrayResponse doGetInstances(@QueryParameter String workspace, @QueryParameter String box) {
            return itemProvider.getInstancesAsJSONArrayResponse(workspace, box);
        }
        
    }
}
