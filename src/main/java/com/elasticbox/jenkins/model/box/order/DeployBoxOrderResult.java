package com.elasticbox.jenkins.model.box.order;

/**
 * Created by serna on 11/27/15.
 */
public class DeployBoxOrderResult<T> {

    private DeployBoxOrder deployBoxOrder;
    private T result;


    public DeployBoxOrderResult(DeployBoxOrder deployBoxOrder, T result) {
        this.deployBoxOrder = deployBoxOrder;
        this.result = result;
    }

    public DeployBoxOrderResult(T result) {
        this.deployBoxOrder = deployBoxOrder;
        this.result = result;
    }

    public DeployBoxOrder getDeployBoxOrder() {
        return deployBoxOrder;
    }

    public T getResult(){
        return result;
    }

}
