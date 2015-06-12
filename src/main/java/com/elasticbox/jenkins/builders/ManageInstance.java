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

import com.elasticbox.jenkins.util.VariableResolver;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class ManageInstance extends ManageObject {

    @DataBoundConstructor
    public ManageInstance(String cloud, String workspace, List<? extends Operation> operations) {
        super(cloud, workspace, operations);
    }

    @Extension
    public static class DescriptorImpl extends ManageObjectDescriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox - Manage Instance";
        }

        @Override
        public List<? extends Descriptor<Operation>> getOperations() {
            return getOperationDescriptors(IOperation.InstanceOperation.class);
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            ManageInstance manageInstance = (ManageInstance) super.newInstance(req, formData);
            for (Operation operation : manageInstance.getOperations()) {
                if (operation instanceof UpdateOperation &&
                        VariableResolver.parseVariables(((UpdateOperation) operation).getVariables()).isEmpty()) {
                    throw new FormException("Update operation must update at least one variable.", "operations");
                }
            }
            return manageInstance;
        }

    }
}
