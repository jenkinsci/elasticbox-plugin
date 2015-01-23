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
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class UpdateOperation extends BoxRequiredOperation implements IOperation.InstanceOperation {

    @DataBoundConstructor
    public UpdateOperation(String box, String boxVersion, String tags, boolean failIfNoneFound, String variables) {
        super(box, boxVersion, tags, failIfNoneFound, variables);
    }

    @Override
    public void perform(ElasticBoxCloud cloud, String workspace, AbstractBuild<?, ?> build, Launcher launcher, TaskLogger logger) throws InterruptedException, IOException {
        logger.info("Executing Update");
        
        VariableResolver resolver = new VariableResolver(cloud.name, workspace, build, logger.getTaskListener());
        JSONArray resolvedVariables = resolver.resolveVariables(getVariables());
        Client client = cloud.getClient();
        DescriptorHelper.removeInvalidVariables(resolvedVariables, DescriptorHelper.getBoxStack(client, getBoxVersion()).getJsonArray());
        // remove empty variables and resolve binding with tags
        for (Iterator iter = resolvedVariables.iterator(); iter.hasNext();) {
            JSONObject variable = (JSONObject) iter.next();
            String variableValue = variable.getString("value");
            if (variableValue.isEmpty()) {
                iter.remove();
            }
        }
        
        Set<String> resolvedTags = resolver.resolveTags(getTags());
        logger.info(MessageFormat.format("Looking for instances with the following tags: {0}", StringUtils.join(resolvedTags, ", ")));
        JSONArray instances = DescriptorHelper.getInstances(resolvedTags, cloud.name, workspace, getBoxVersion());        
        if (!canPerform(instances, logger)) {
            return;
        }

        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;            
            client.updateInstance(instanceJson, resolvedVariables, getBoxVersion());
            String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), instanceJson);
            logger.info(MessageFormat.format("Updated instance {0}", instancePageUrl));            
        }
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor {

        @Override
        public String getDisplayName() {
            return "Update";
        }

        @Override
        public Operation newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String variablesStr = formData.getString("variables");
            JSONArray variables = VariableResolver.parseVariables(variablesStr);
            boolean update = false;
            for (Object variable : variables) {
                JSONObject variableJson = (JSONObject) variable;
                if (StringUtils.isNotBlank(variableJson.getString("value"))) {
                    update = true;
                    break;
                }
            }
            if (!update) {
                throw new FormException("No variable is changed for Update operation.", "variables");
            }
            
            return super.newInstance(req, formData);
        }

    }
}
