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
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueListener;
import jenkins.model.Jenkins;
/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class ElasticBoxQueueListener extends QueueListener {

    @Override
    public void onLeft(hudson.model.Queue.LeftItem li) {
        if (li.isCancelled()) {
            if (li.task instanceof AbstractProject) {
                AbstractProject project = (AbstractProject) li.task;
                // check if there is a single-use slave being launched for this build, disable it
                SingleUseSlaveBuildOption singleUseOption = null;
                InstanceCreator instanceCreator = null;
                for (Object buildWrapper : ((BuildableItemWithBuildWrappers) project).getBuildWrappersList().toMap().values()) {
                    if (buildWrapper instanceof InstanceCreator) {
                        instanceCreator = (InstanceCreator) buildWrapper;
                    } else if (buildWrapper instanceof SingleUseSlaveBuildOption) {
                        singleUseOption = (SingleUseSlaveBuildOption) buildWrapper;
                    }
                    if (instanceCreator != null && singleUseOption != null) {
                        break;
                    }
                }

                if (singleUseOption != null && instanceCreator != null) {
                    LabelAtom label = ElasticBoxLabelFinder.getLabel(instanceCreator.getSlaveConfiguration(), true);
                    for (Node node : Jenkins.getInstance().getNodes()) {
                        if (node instanceof ElasticBoxSlave && label.matches(node)) {
                            ((ElasticBoxSlave) node).setDeletable(true);
                            break;
                        }
                    }
                }     
            }
        }
    }
    
}
