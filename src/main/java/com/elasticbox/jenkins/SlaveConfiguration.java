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

import com.elasticbox.Client;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveConfiguration implements Describable<SlaveConfiguration> {
    private static final Logger LOGGER = Logger.getLogger(SlaveConfiguration.class.getName());

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
    private final int retentionTime;
    private final int executors;
    private final int launchTimeout;
    
    private transient Set<LabelAtom> labelSet;

    @DataBoundConstructor
    public SlaveConfiguration(String workspace, String box, String boxVersion, String profile, int maxInstances, 
            String environment, String variables, String labels, String description, String remoteFS, Node.Mode mode, 
            int retentionTime, int executors, int launchTimeout) {
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
        this.retentionTime = retentionTime;
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

    public int getRetentionTime() {
        return retentionTime;
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

        @Override
        public String getDisplayName() {
            return null;
        }
        
        private Client createClient(String endpointUrl, String username, String password) {
            if (StringUtils.isBlank(endpointUrl) || StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
                return null;
            }
            
            Client client = ClientCache.getClient(endpointUrl, username, password);
            if (client == null) {
                client = new Client(endpointUrl, username, password);
                try {  
                    client.connect();
                } catch (IOException ex) {
                    client = null;
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            
            return client;
        }

        public ListBoxModel doFillWorkspaceItems(@RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password) {
            return ElasticBoxItemProvider.getWorkspaces(createClient(endpointUrl, username, password));
        }
        
        public ListBoxModel doFillBoxItems(@RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @QueryParameter String workspace) {
            return ElasticBoxItemProvider.getBoxes(createClient(endpointUrl, username, password), workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @QueryParameter String box) {
            return ElasticBoxItemProvider.getBoxVersions(createClient(endpointUrl, username, password), box);
        }

        public ListBoxModel doFillProfileItems(@RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @QueryParameter String workspace, @QueryParameter String box) {                
            return ElasticBoxItemProvider.getProfiles(createClient(endpointUrl, username, password), workspace, box);
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetBoxStack(
                @RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @QueryParameter String box, 
                @QueryParameter String boxVersion) {
            return ElasticBoxItemProvider.getBoxStack(createClient(endpointUrl, username, password),
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public ElasticBoxItemProvider.JSONArrayResponse doGetInstances(
                @RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @QueryParameter String workspace, 
                @QueryParameter String box, 
                @QueryParameter String boxVersion) {
            return ElasticBoxItemProvider.getInstancesAsJSONArrayResponse(createClient(endpointUrl, username, password),
                    workspace, StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }
        
    }

}
