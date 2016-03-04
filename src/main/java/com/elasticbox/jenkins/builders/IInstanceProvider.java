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
import hudson.model.AbstractBuild;

/**
 * Interface for build step to implements to provide instance to subsequent build steps of a build.
 * @author Phong Nguyen Le
 */
public interface IInstanceProvider {

    /**
     * Gets the unique ID of the build step.
     * @return the unique ID
     */
    String getId();

    /**
     * Gets the ID of an instance that can be used in a subsequent build step of a build.
     * @param build that is being handled
     * @return the ID of an existing instance or <code>null</code>
     */
    String getInstanceId(AbstractBuild build);

    ElasticBoxCloud getElasticBoxCloud();
}
