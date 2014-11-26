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

import com.elasticbox.jenkins.DescriptorHelper;
import hudson.RelativePath;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class BoxRequiredOperation extends Operation {
    private final String box;
    private final String boxVersion;
    private final String variables;    
    
    public BoxRequiredOperation(String box, String boxVersion, String tags, String variables) {
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
    
    public static abstract class Descriptor extends OperationDescriptor {

        public ListBoxModel doFillBoxItems(@RelativePath("..") @QueryParameter String cloud, 
                @RelativePath("..") @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(cloud, workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@RelativePath("..") @QueryParameter String cloud, 
                @QueryParameter String workspace, @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(cloud, workspace, box);
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
