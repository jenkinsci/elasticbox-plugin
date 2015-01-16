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

package com.elasticbox.jenkins.triggers;

import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import java.io.IOException;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class BuildManager<T extends IBuildHandler> implements ExtensionPoint {
    public abstract T createBuildHandler(AbstractProject<?, ?> project, boolean newTrigger) throws IOException;
}
