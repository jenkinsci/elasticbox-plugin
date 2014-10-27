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
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.slaves.SlaveComputer;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class BuildListener extends RunListener<AbstractBuild> {

    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener) {
        Node node = build.getBuiltOn();
        if (node instanceof ElasticBoxSlave) {
            ElasticBoxSlave slave = (ElasticBoxSlave) node;
            AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
            if (slave.isSingleUse() || (slaveConfig != null && slaveConfig.getRetentionTime() == 0)) {
                SlaveComputer computer = slave.getComputer();
                if (computer != null) {
                    computer.setAcceptingTasks(false);
                }
                slave.setDeletable(true);
            }
        }        
    }

}
