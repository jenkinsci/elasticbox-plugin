/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
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

import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;

import java.io.IOException;

/**
 * Receives notification about ElasticBox instances/boxes that were deployed/managed by the ElasticBox build steps.
 * @author Phong Nguyen Le
 */
public abstract class BuilderListener implements ExtensionPoint {

    public abstract void onDeploying(AbstractBuild<?, ?> build, String instanceId, ElasticBoxCloud cloud)
            throws IOException, InterruptedException;

    public abstract void onTerminating(AbstractBuild<?, ?> build, String instanceId, ElasticBoxCloud cloud)
            throws IOException, InterruptedException;

}
