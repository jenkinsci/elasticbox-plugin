/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.services.deployment.execution.order;

/**
 * Created by serna on 1/20/16.
 */
public class ApplicationBoxDeploymentOrder extends AbstractDeployBoxOrder {

    private String [] requirements;

    public ApplicationBoxDeploymentOrder(boolean waitForDone, String box, String boxVersion,
                                         String[] tags, String name, String owner, String expirationTime,
                                         String expirationOperation, String [] requirements) {

        super(waitForDone, box, boxVersion, tags, name, owner, expirationTime, expirationOperation);

        this.requirements = requirements;
    }

    public String[] getRequirements() {
        return requirements;
    }
}
