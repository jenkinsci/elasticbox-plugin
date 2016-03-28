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
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;

import jenkins.model.Jenkins;

import java.io.IOException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class InstanceCreationQueueDecisionHandler extends Queue.QueueDecisionHandler {
    private static final Logger LOGGER = Logger.getLogger(InstanceCreationQueueDecisionHandler.class.getName());

    @Override
    public boolean shouldSchedule(Queue.Task task, List<Action> actions) {
        if (task instanceof AbstractProject && task instanceof BuildableItemWithBuildWrappers) {
            AbstractProject project = (AbstractProject) task;
            InstanceCreator instanceCreator = null;
            boolean singleUse = false;
            for (Object buildWrapper : ((BuildableItemWithBuildWrappers)task).getBuildWrappersList().toMap().values()) {
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
                for (Queue.Item item : Queue.getInstance().getItems(task)) {
                    boolean shouldScheduleItem = false;
                    for (Queue.QueueAction action: item.getActions(Queue.QueueAction.class)) {
                        shouldScheduleItem |= action.shouldSchedule(actions);
                    }
                    for (Queue.QueueAction action: Util.filter(actions,Queue.QueueAction.class)) {
                        shouldScheduleItem |= action.shouldSchedule(item.getActions());
                    }
                    if (!shouldScheduleItem) {
                        return false;
                    }
                }

                ProjectSlaveConfiguration config = instanceCreator.getSlaveConfiguration();
                LabelAtom label = ElasticBoxLabelFinder.getLabel(config, singleUse);
                if (singleUse) {
                    try {
                        LOGGER.info("Launching single use slave for task: " + project.getAssignedLabelString() );
                        LaunchAttempts.resetAttempts(config.getId() );
                        ElasticBoxSlaveHandler.launchSingleUseSlave(config, label.getName() );
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
