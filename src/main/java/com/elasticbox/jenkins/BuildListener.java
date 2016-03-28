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

package com.elasticbox.jenkins;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildListener extends RunListener<AbstractBuild> {
    private static final Logger LOGGER = Logger.getLogger(BuildListener.class.getName());

    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener) {
        try {
            Node node = build.getBuiltOn();
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                slave.incrementBuilds();
                if (slave.hasExpired() || requiresGlobalSingleUseSlave(build.getProject())) {
                    LOGGER.info(build.toString() + " has completed. Marking slave for termination - " + slave);
                    slave.markForTermination();
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    private static boolean requiresGlobalSingleUseSlave(AbstractProject project) {
        ElasticBoxBuildWrappers ebxBuildWrappers = ElasticBoxBuildWrappers.getElasticBoxBuildWrappers(project);
        return ebxBuildWrappers.singleUseSlaveOption != null && ebxBuildWrappers.instanceCreator == null;
    }

}
