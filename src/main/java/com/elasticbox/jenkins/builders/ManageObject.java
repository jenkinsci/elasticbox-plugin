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

import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.util.TaskLogger;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.slaves.Cloud;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class ManageObject extends AbstractBuilder {
    private final List<? extends Operation> operations;
    
    public ManageObject(String cloud, String workspace, List<? extends Operation> operations) {
        super(cloud, workspace);
        this.operations = operations;
    }

    public List<? extends Operation> getOperations() {
        return operations != null ? Collections.unmodifiableList(operations) : Collections.EMPTY_LIST;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        TaskLogger logger = new TaskLogger(listener);
        logger.info("Executing Manage Instance build step");

        Cloud ebCloud = Jenkins.getInstance().getCloud(getCloud());
        if (!(ebCloud instanceof ElasticBoxCloud)) {
            throw new IOException(MessageFormat.format("Invalid cloud name: {0}", getCloud()));
        }
        
        for (Operation operation : operations) {
            operation.perform((ElasticBoxCloud) ebCloud, getWorkspace(), build, launcher, logger);
        }
        
        return true;
    }
    
    public static abstract class ManageObjectDescriptor extends AbstractBuilderDescriptor {
        public abstract List<? extends Descriptor<Operation>> getOperations();
        
        protected List<? extends Descriptor<Operation>> getOperationDescriptors(Class type) {
            List<Descriptor<Operation>> operationDescriptors = new ArrayList<Descriptor<Operation>>();
            for (Descriptor<Operation> descriptor : Jenkins.getInstance().getDescriptorList(Operation.class)) {
                if (type.isAssignableFrom(descriptor.clazz)) {
                    operationDescriptors.add(descriptor);
                }
            }
            return operationDescriptors;            
        }

    }
}
