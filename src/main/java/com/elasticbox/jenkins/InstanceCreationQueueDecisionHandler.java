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
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class InstanceCreationQueueDecisionHandler extends Queue.QueueDecisionHandler {
    private static final Logger LOGGER = Logger.getLogger(InstanceCreationQueueDecisionHandler.class.getName());

    @Override
    public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
        if (p instanceof AbstractProject && p instanceof Project) {
            AbstractProject project = (AbstractProject) p;
            InstanceCreator instanceCreator = null;
            boolean singleUse = false;
            for (Object buildWrapper : ((Project) p).getBuildWrappers().values()) {
                if (buildWrapper instanceof InstanceCreator) {
                    instanceCreator = (InstanceCreator) buildWrapper;
                } else if (buildWrapper instanceof SingleUseSlaveBuildOption) {
                    singleUse = true;
                }
                if (instanceCreator != null && singleUse) {
                    break;
                }
            }
            if (instanceCreator != null) {
                for(Queue.Item item : Queue.getInstance().getItems(p)) {
                    boolean shouldScheduleItem = false;
                    for (Queue.QueueAction action: item.getActions(Queue.QueueAction.class)) {
                        shouldScheduleItem |= action.shouldSchedule(actions);
                    }
                    for (Queue.QueueAction action: Util.filter(actions,Queue.QueueAction.class)) {
                        shouldScheduleItem |= action.shouldSchedule(item.getActions());
                    }
                    if(!shouldScheduleItem) {
                        return false;
                    }
                }
                
                LabelAtom label = ElasticBoxLabelFinder.getLabel(instanceCreator.getProfile(), singleUse);
                if (singleUse) {
                    try {
                        ElasticBoxSlave slave = new ElasticBoxSlave(instanceCreator.getProfile(), singleUse, ElasticBoxCloud.getInstance());
                        slave.setInUse(true);
                        Jenkins.getInstance().addNode(slave);
                        ElasticBoxSlaveHandler.submit(slave);
                    } catch (Descriptor.FormException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
                try {
                    project.setAssignedLabel(label);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
                
            }
        }
        
        return true;
    }
    
}
