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

import com.elasticbox.jenkins.util.Condition;
import com.thoughtworks.xstream.XStream;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.slaves.Cloud;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Initializers {
    private static final Logger LOGGER = Logger.getLogger(Initializers.class.getName());

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void tagSlaveInstances() throws IOException {
        LOGGER.finest("Tagging slave instances");

        // wait for nodes to be set
        new Condition() {
            @Override
            public boolean satisfied() {
                return Jenkins.getInstance().getNodes() != null;
            }
        }.waitUntilSatisfied(3000);

        ElasticBoxExecutor.threadPool.submit(new Runnable() {

            public void run() {
                try {
                    SlaveInstanceManager manager = new SlaveInstanceManager();
                    for (JSONObject instance : manager.getInstances()) {
                        ElasticBoxSlave slave = manager.getSlave(instance.getString("id"));
                        ElasticBoxSlaveHandler.getInstance().tagSlaveInstance(instance, slave);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Error tagging slave instances", ex);
                }
            }

        });
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void setSlaveConfigurationId() throws IOException {
        LOGGER.finest("Fixing old slave configurations");
        boolean saveNeeded = false;

        // set the ID of those slave configurations that don't have an ID assigned yet
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                ElasticBoxCloud ebxCloud = (ElasticBoxCloud) cloud;
                for (SlaveConfiguration slaveConfig : ebxCloud.getSlaveConfigurations()) {
                    if (StringUtils.isBlank(slaveConfig.getId())) {
                        slaveConfig.setId(UUID.randomUUID().toString());
                        saveNeeded = true;
                    }
                }
            }
        }

        if (saveNeeded) {
            Jenkins.getInstance().save();
        }
    }
}
