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

import hudson.Extension;
import hudson.model.Descriptor;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Phong Nguyen Le
 */
public class ManageBox extends ManageObject {

    @DataBoundConstructor
    public ManageBox(String cloud, String workspace, List<? extends Operation> operations) {
        super(cloud, workspace, operations);
    }
    
    @Extension
    public static class DescriptorImpl extends ManageObjectDescriptor {

        @Override
        public String getDisplayName() {
            return "ElasticBox - Manage Box";
        }

        @Override
        public List<? extends Descriptor<Operation>> getOperations() {
            return getOperationDescriptors(IOperation.BoxOperation.class);
        }
        
    }
}
