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
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.Extension;
import hudson.Launcher;
import hudson.RelativePath;
import hudson.model.AbstractBuild;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class UpdateOperation extends Operation {
    private final String box;
    private final String boxVersion;
    private final String variables;    
    
    @DataBoundConstructor
    public UpdateOperation(String box, String boxVersion, String tags, String variables) {
        super(tags);
        this.box = box;
        this.boxVersion = boxVersion;    
        this.variables = variables;
    }

    public String getBox() {
        return box;
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public String getVariables() {
        return variables;
    }
    
    @Override
    public void perform(ElasticBoxCloud cloud, String workspace, AbstractBuild<?, ?> build, Launcher launcher, TaskLogger logger) throws InterruptedException, IOException {
        logger.info("Executing Update");
        
        VariableResolver resolver = new VariableResolver(build, logger.getTaskListener());
        JSONArray resolvedVariables = DescriptorHelper.parseVariables(variables);
        for (Object variable : resolvedVariables) {
            resolver.resolve((JSONObject) variable);
        }       
        Client client = ClientCache.getClient(cloud.name);
        DescriptorHelper.removeInvalidVariables(resolvedVariables, DescriptorHelper.getBoxStack(client, boxVersion).getJsonArray());
        // remove empty variables
        for (Iterator iter = resolvedVariables.iterator(); iter.hasNext();) {
            JSONObject variable = (JSONObject) iter.next();
            if (variable.getString("value").isEmpty()) {
                iter.remove();
            }
        }
        Set<String> resolvedTags = getResolvedTags(resolver);
        logger.info(MessageFormat.format("Looking for instances with the following tags: {0}", StringUtils.join(resolvedTags, ", ")));
        JSONArray instances = DescriptorHelper.getInstances(resolvedTags, cloud.name, workspace, true);        
        if (instances.isEmpty()) {
            logger.info("No instance found with the specified tags");
            return;
        }

        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;
            
            client.updateInstance(instanceJson, resolvedVariables);
            String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), instanceJson);
            logger.info(MessageFormat.format("Updated instance {0}", instancePageUrl));            
        }
    }
    
    @Extension
    public static final class DescriptorImpl extends OperationDescriptor {

        @Override
        public String getDisplayName() {
            return "Update";
        }

        public ListBoxModel doFillBoxItems(@RelativePath("..") @QueryParameter String cloud, 
                @RelativePath("..") @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(cloud, workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@RelativePath("..") @QueryParameter String cloud, 
                @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(cloud, box);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(@RelativePath("..") @QueryParameter String cloud, 
                @QueryParameter String box, @QueryParameter String boxVersion) {
            DescriptorHelper.JSONArrayResponse response = DescriptorHelper.getBoxStack(cloud, StringUtils.isBlank(boxVersion) ? box : boxVersion);
            // reset the variable of all variable to empty string so the UI will save variables with non-empty value and
            // only those variables will be updated for every instance with matching tags
            for (Object boxJson : response.getJsonArray()) {
                for (Object variable : ((JSONObject) boxJson).getJSONArray("variables")) {
                    JSONObject variableJson = (JSONObject) variable;
                    if (!"Binding".equals(variableJson.get("type"))) {
                        ((JSONObject) variable).put("value", StringUtils.EMPTY);
                    }
                }
            }
            return response;
        }

        public DescriptorHelper.JSONArrayResponse doGetInstances(@RelativePath("..") @QueryParameter String cloud, 
                @RelativePath("..") @QueryParameter String workspace, @QueryParameter String box, 
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJSONArrayResponse(cloud, workspace, 
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }
                
    }
}
