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
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveConfiguration extends AbstractSlaveConfiguration {
    public static final String SLAVE_CONFIGURATIONS = "slaveConfigurations";
    
    private static final Logger LOGGER = Logger.getLogger(SlaveConfiguration.class.getName());

    @DataBoundConstructor
    public SlaveConfiguration(String id, String workspace, String box, String boxVersion, String profile, 
            int minInstances, int maxInstances, String environment, String variables, String labels, String description, 
            String remoteFS, Node.Mode mode, int retentionTime, int executors, int launchTimeout) {
        super(id, workspace, box, boxVersion, profile, minInstances, maxInstances, environment, variables, labels, 
                description, remoteFS, mode, retentionTime, executors, launchTimeout);
    }    
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<AbstractSlaveConfiguration> {

        @Override
        public String getDisplayName() {
            return "Slave Configuration";
        }

        private Client createClient(String endpointUrl, String username, String password, String token) {
            if (StringUtils.isBlank(endpointUrl) || 
                    (StringUtils.isBlank(token) && (StringUtils.isBlank(username) || StringUtils.isBlank(password)))) {
                return null;
            }
            
            Client client = ClientCache.getClient(endpointUrl, username, password, token);
            if (client == null) {
                if (StringUtils.isNotBlank(token)) {
                    client = new Client(endpointUrl, token);
                } else {
                    client = new Client(endpointUrl, username, password);
                }
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
                @RelativePath("..") @QueryParameter String password,
                @RelativePath("..") @QueryParameter String token) {
            return DescriptorHelper.getWorkspaces(createClient(endpointUrl, username, password, token));
        }
        
        public ListBoxModel doFillBoxItems(@RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(createClient(endpointUrl, username, password, token), workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(createClient(endpointUrl, username, password, token), box);
        }
        
        public FormValidation doCheckBoxVersion(@QueryParameter String value,
                @RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String box) {
            return DescriptorHelper.checkSlaveBox(createClient(endpointUrl, username, password, token), 
                    StringUtils.isBlank(value) ? box : value);
        }

        public ListBoxModel doFillProfileItems(@RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace, @QueryParameter String box) {                
            return DescriptorHelper.getProfiles(createClient(endpointUrl, username, password, token), workspace, box);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(
                @RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String box, 
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(createClient(endpointUrl, username, password, token),
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public DescriptorHelper.JSONArrayResponse doGetInstances(
                @RelativePath("..") @QueryParameter String endpointUrl, 
                @RelativePath("..") @QueryParameter String username, 
                @RelativePath("..") @QueryParameter String password, 
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace, 
                @QueryParameter String box, 
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJSONArrayResponse(createClient(endpointUrl, username, password, token),
                    workspace, StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }
        
    }

}
