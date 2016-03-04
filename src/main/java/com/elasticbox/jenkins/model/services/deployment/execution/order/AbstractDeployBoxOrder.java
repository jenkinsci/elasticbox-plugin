package com.elasticbox.jenkins.model.services.deployment.execution.order;

import org.apache.commons.lang.builder.ToStringBuilder;

public abstract class AbstractDeployBoxOrder {

    private boolean waitForDone;
    private String box;
    private String boxVersion;
    private String [] tags;
    private String name;
    private String owner;
    private String expirationTime;
    private String expirationOperation;

    public AbstractDeployBoxOrder(boolean waitForDone, String boxToDeployId, String boxVersion, String[] tags,
                                  String name, String owner, String expirationTime, String expirationOperation) {
        this.waitForDone = waitForDone;
        this.box = boxToDeployId;
        this.boxVersion = boxVersion;
        this.tags = tags;
        this.name = name;
        this.owner = owner;
        this.expirationTime = expirationTime;
        this.expirationOperation = expirationOperation;
    }

    public boolean isWaitForDone() {
        return waitForDone;
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

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getExpirationTime() {
        return expirationTime;
    }

    public String getExpirationOperation() {
        return expirationOperation;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
