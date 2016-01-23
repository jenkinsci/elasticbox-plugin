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

package com.elasticbox.jenkins.model.services.deployment.execution.task;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.execution.context.ApplicationBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.task.Task;
import com.elasticbox.jenkins.model.services.task.TaskDependingOnOtherTasks;
import com.elasticbox.jenkins.model.services.task.TaskException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 12/9/15.
 */
public class DeployApplicationBoxTask extends TaskDependingOnOtherTasks<List<Instance>> {

    private static final Logger logger = Logger.getLogger(DeployApplicationBoxTask.class.getName());

    private ApplicationBoxDeploymentContext context;

    private DeployApplicationBoxTask(Builder builder) {
        super(builder);
        this.context = builder.context;
    }

    @Override
    protected void performExecute() throws TaskException {

        try {
            this.result = context.getDeploymentOrderRepository().deploy(context);
        } catch (RepositoryException e) {
            logger.log(Level.SEVERE, "Error executing task DeployApplicationBoxTask", e);
            throw new TaskException("Error executing task DeployApplicationBoxTask", e);
        }

        logger.log(Level.INFO, "Request to deploy ApplicationBox: "+context.getOrder().getName()+" executed, waiting for instances to finish deployment");
        context.getLogger().info("ApplicationBox: {0} deployment request executed", context.getOrder().getName());
    }

    @Override
    public boolean isDone() {

        final List<Instance> result = getResult();
        if (result != null && !result.isEmpty()){
            if(allDependingOnTasksDone()){
                return true;
            }
        }

        return false;
    }

    public List<Instance> succesfullyDeployedInstances(){
        if (isDone()){
            for (Task<?> task: getDependingOnTasks()) {
                if (task instanceof CheckInstancesDeployedTask) {
                    CheckInstancesDeployedTask instancesDeployedTask = (CheckInstancesDeployedTask) task;
                    return instancesDeployedTask.getResult();
                }
            }
        }
        return new ArrayList<Instance>();
    }

    @Override
    protected boolean prepareDependingOnTasks(List<Instance> mainTaskResult, List<Task<?>> dependingOnTasks) {
        for (Task<?> dependingOnTask : dependingOnTasks) {
            if (dependingOnTask instanceof CheckInstancesDeployedTask) {
                CheckInstancesDeployedTask onTask = (CheckInstancesDeployedTask) dependingOnTask;
                onTask.setInstances(mainTaskResult);

                for (Instance instance: mainTaskResult) {
                    final String instancePageURL = instance.getInstancePageURL(context.getCloud().getEndpointUrl());
                    context.getLogger().info("Waiting for the deployment of the instance {0} to finish", instancePageURL);
                    logger.log(Level.INFO, "Waiting for the deployment of the instance "+instancePageURL+" to finish");
                }

                return true;
            }
        }
        return false;
    }


    public static class Builder extends AbstractBuilder<Builder, DeployApplicationBoxTask> {

        private ApplicationBoxDeploymentContext context;

        public Builder withApplicationBoxDeploymentContext(ApplicationBoxDeploymentContext context){
            this.context = context;
            this.timeout = new Long(Constants.DEFAULT_DEPLOYMENT_APPLICATION_BOX_TIMEOUT);
            return this;
        }


        @Override
        public DeployApplicationBoxTask build() {
            return new DeployApplicationBoxTask(this);
        }

    }

}
