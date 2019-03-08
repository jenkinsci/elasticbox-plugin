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

import java.io.IOException;

/**
 * IOperation interface method declarations.
 *
 * @author Phong Nguyen Le.
 */
public interface IOperation {

    public void perform(
        ElasticBoxCloud cloud,
        String workspace, AbstractBuild<?, ?> build,
        Launcher launcher,
        TaskLogger logger) throws InterruptedException, IOException;

    public interface InstanceOperation extends IOperation {

    }

    public interface BoxOperation extends IOperation {

    }
}
