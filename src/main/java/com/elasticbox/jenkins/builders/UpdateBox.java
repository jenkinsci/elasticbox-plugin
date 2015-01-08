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
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class UpdateBox extends AbstractBuilder {
    private final String box;
    private final String variables;

    @DataBoundConstructor
    public UpdateBox(String cloud, String workspace, String box, String variables) {
        super(cloud, workspace);
        this.box = box;
        this.variables = variables;
    }

    public String getBox() {
        return box;
    }

    public String getVariables() {
        return variables;
    }        

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) 
            throws InterruptedException, IOException {
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Update Box");
        
        VariableResolver resolver = new VariableResolver(getCloud(), getWorkspace(), build, logger.getTaskListener());
        JSONArray resolvedVariables = resolver.resolveVariables(variables);
        Client client = ClientCache.getClient(getCloud());
        DescriptorHelper.removeInvalidVariables(resolvedVariables, DescriptorHelper.getBoxStack(client, box).getJsonArray());
        for (Object variable : resolvedVariables) {
            JSONObject variableJson = (JSONObject) variable;
            variableJson.put("value", new File(variableJson.getString("value")).toURI().toString());
        }
        JSONObject boxJson = client.updateBox(box, resolvedVariables);
        String boxPageUrl = Client.getPageUrl(client.getEndpointUrl(), boxJson);
        logger.info(MessageFormat.format("Updated box {0}", boxPageUrl));    
        
        return true;
    }
        
    @Extension
    public static class DescriptorImpl extends AbstractBuilderDescriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox - Update Box";
        }

        public ListBoxModel doFillBoxItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(cloud, workspace);
        }
        
        public DescriptorHelper.JSONArrayResponse doGetBoxStack(@QueryParameter String cloud, 
                @QueryParameter String box) {            
            DescriptorHelper.JSONArrayResponse response = DescriptorHelper.getBoxStack(cloud, box);
            // reset the variable of all variable to empty string so the UI will save only variables with non-empty value
            for (Object boxObject : response.getJsonArray()) {
                JSONObject boxJson = (JSONObject) boxObject;
                JSONArray fileVariables = new JSONArray();
                for (Object variable : boxJson.getJSONArray("variables")) {
                    JSONObject variableJson = (JSONObject) variable;
                    if ("File".equals(variableJson.get("type"))) {
                        variableJson.put("value", StringUtils.EMPTY);
                        fileVariables.add(variableJson);
                    }
                }
                boxJson.put("variables", fileVariables);
            }
            return response;
        }
        
    }
}
