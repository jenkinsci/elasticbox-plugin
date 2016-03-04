package com.elasticbox.jenkins.model.services.deployment.execution.order;

public class DeployBoxOrderResult<T> {

    private T result;

    public DeployBoxOrderResult(T result) {
        this.result = result;
    }

    public T getResult() {
        return result;
    }

}
