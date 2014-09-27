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

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.slaves.Cloud;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public class Initializers {
    private static final Logger LOGGER = Logger.getLogger(Initializers.class.getName());

    @Initializer(after = InitMilestone.COMPLETED)
    public static void tagSlaveInstances() throws IOException {
        LOGGER.info("Tagging slave instances");
        SlaveInstanceManager manager = new SlaveInstanceManager();
        for (JSONObject instance : manager.getInstances()) {
            ElasticBoxSlave slave = manager.getSlave(instance.getString("id"));
            ElasticBoxSlaveHandler.tagSlaveInstance(instance, slave);
        }        
    }
    
    @Initializer(after = InitMilestone.COMPLETED)
    public static void setSlaveConfigurationId() throws IOException {
        boolean saveNeeded = false;
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
