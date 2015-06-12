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

import hudson.model.AbstractProject;
import hudson.model.BuildableItemWithBuildWrappers;

/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxBuildWrappers {
    public final InstanceCreator instanceCreator;
    public final SingleUseSlaveBuildOption singleUseSlaveOption;

    private ElasticBoxBuildWrappers(InstanceCreator instanceCreator, SingleUseSlaveBuildOption singleUseSlaveOption) {
        this.instanceCreator = instanceCreator;
        this.singleUseSlaveOption = singleUseSlaveOption;
    }

    public static final ElasticBoxBuildWrappers getElasticBoxBuildWrappers(AbstractProject project) {
        SingleUseSlaveBuildOption singleUseOption = null;
        InstanceCreator instanceCreator = null;
        if (project instanceof BuildableItemWithBuildWrappers) {
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
        }
        return new ElasticBoxBuildWrappers(instanceCreator, singleUseOption);
    }
}
