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
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class Operation implements IOperation, Describable<Operation> {
    private final String tags;
    
    protected Operation(String tags) {
        this.tags = tags;
    }

    public String getTags() {
        return tags;
    }

    public Descriptor<Operation> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }
    
    public static abstract class OperationDescriptor extends Descriptor<Operation> {
    }
}
