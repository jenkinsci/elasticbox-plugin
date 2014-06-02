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
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class InstanceCreationQueueDecisionHandler extends Queue.QueueDecisionHandler {

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
                Queue.Item queueItem = Queue.getInstance().getItem(p);
                if (queueItem != null && queueItem.getAssignedLabel() != null) {
                    return false;
                }
                
                LabelAtom label = ElasticBoxLabelFinder.getLabel(instanceCreator.getProfile(), singleUse);
                if (singleUse) {
                    try {
                        ElasticBoxSlave slave = new ElasticBoxSlave(UUID.randomUUID().toString(), singleUse);
                        slave.setProfileId(instanceCreator.getProfile());
                        slave.setCloud(ElasticBoxCloud.getInstance());
                        slave.setInUse(true);
                        Jenkins.getInstance().addNode(slave);
                        ElasticBoxSlaveHandler.submit(slave);
                    } catch (Descriptor.FormException ex) {
                        Logger.getLogger(InstanceCreationQueueDecisionHandler.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (IOException ex) {
                        Logger.getLogger(InstanceCreationQueueDecisionHandler.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
                try {
                    project.setAssignedLabel(label);
                } catch (IOException ex) {
                    Logger.getLogger(InstanceCreationQueueDecisionHandler.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                }
                
            }
        }
        
        return true;
    }
    
}
