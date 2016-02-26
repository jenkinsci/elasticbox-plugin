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

package com.elasticbox.jenkins.model.services.instances.execution.order;

public class UpdateInstancesOrder implements ManageInstanceOrder {

    private boolean waitForCompletion;
    private int waitForCompletionTimeout;
    private String box;
    private String boxVersion;
    private String [] tags;
    private String [] variables;
    private String workspace;


    public UpdateInstancesOrder(
        boolean waitForCompletion,
        String box,
        String boxVersion,
        String[] tags,
        String[] variables,
        String workspace,
        int waitForCompletionTimeout) {

        this.waitForCompletionTimeout = waitForCompletionTimeout;
        this.waitForCompletion = waitForCompletion;
        this.box = box;
        this.boxVersion = boxVersion;
        this.tags = tags;
        this.variables = variables;
        this.workspace = workspace;
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    public int getWaitForCompletionTimeout() {
        return waitForCompletionTimeout;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public String[] getTags() {
        return tags;
    }

    public String[] getVariables() {
        return variables;
    }
}
