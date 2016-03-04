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

import com.elasticbox.jenkins.util.TaskLogger;
import hudson.AbortException;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;

public abstract class Operation implements IOperation, Describable<Operation> {
    private final String tags;

    protected Operation(String tags) {
        this.tags = tags;
    }

    public String getTags() {
        return tags;
    }

    protected abstract boolean failIfNoInstanceFound();

    protected boolean canPerform(JSONArray instances, TaskLogger logger) throws AbortException {
        if (!instances.isEmpty()) {
            return true;
        }

        final String message = "No instance found with the specified tags";
        if (failIfNoInstanceFound()) {
            throw new AbortException(message);
        }

        logger.info(message);
        return false;
    }

    @Override
    public Descriptor<Operation> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public abstract static class OperationDescriptor extends Descriptor<Operation> {
    }
}
