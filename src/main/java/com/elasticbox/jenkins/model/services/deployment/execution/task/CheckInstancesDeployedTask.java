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

import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.execution.context.AbstractBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.task.ScheduledPoolingTask;
import com.elasticbox.jenkins.model.services.task.TaskException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 1/20/16.
 */
public class CheckInstancesDeployedTask extends ScheduledPoolingTask<List<Instance>> {

    private static final Logger logger = Logger.getLogger(CheckInstancesDeployedTask.class.getName());

    private static final long DEFAULT_DELAY = 360;
    private static final long DEFAULT_INITIAL_DELAY = 3;
    private static final long DEFAULT_TIMEOUT = 3600;

    private int okCounter = 0;
    private int counter = 0;
    private List<Instance> instances =  new ArrayList<>();
    private AbstractBoxDeploymentContext deploymentContext;

    public CheckInstancesDeployedTask(AbstractBoxDeploymentContext deploymentContext, List<Instance>  instances, long delay, long initialDelay, long timeout) {
        super(delay, initialDelay, timeout );
        this.instances = instances;
        this.deploymentContext = deploymentContext;
    }

    public CheckInstancesDeployedTask(AbstractBoxDeploymentContext deploymentContext, long delay, long initialDelay, long timeout) {
        this(deploymentContext, null, delay, initialDelay, timeout);
    }

    public CheckInstancesDeployedTask(AbstractBoxDeploymentContext deploymentContext) {
        this(deploymentContext, null, DEFAULT_DELAY, DEFAULT_INITIAL_DELAY, DEFAULT_TIMEOUT);
    }

    @Override
    protected void performExecute() throws TaskException {
        if(!instances.isEmpty()){
            logger.log(Level.SEVERE, "Error executing task: "+this.getClass().getSimpleName()+", there are no instances to check");
            return;
        }
        try {
            counter++;

            String [] ids = new String[instances.size()];
            int instanceCounter = 0;
            for (Instance instance: getInstances()){
                ids[instanceCounter] = instance.getId();
                instanceCounter++;
            }
            final String owner = deploymentContext.getOrder().getOwner();
            result = deploymentContext.getInstanceRepository().getInstances(owner, ids);

        } catch (RepositoryException e) {
            logger.log(Level.SEVERE, "Error executing task: CheckInstancesDeployedTask",e);
            throw new TaskException("Error executing task: CheckInstancesDeployedTask",e);
        }
    }

    @Override
    public boolean isDone() {
        final List<Instance> instances = getResult();
        if (instances != null && !instances.isEmpty()){
            boolean done = true;
            for (Instance instance : instances) {
                if(instance.getState() != Instance.State.DONE){
                    done = false;
                    break;
                }
            }
            if (done ){
                okCounter++;
                if(okCounter == 2){
                    logger.log(Level.INFO, "CheckInstancesDeployedTask executed: ["+counter+"] times, all instances were DONE ["+okCounter+"] times");
                    deploymentContext.getLogger().info("CheckInstancesDeployedTask executed: {0} times, all instances were DONE {1} times", counter, okCounter);
                    return true;
                }
                logger.log(Level.INFO, "CheckInstancesDeployedTask executed: ["+counter+"] times, all instances were DONE ["+okCounter+"] times");
                deploymentContext.getLogger().info("CheckInstancesDeployedTask executed: {0} times, all instances were DONE {1} times", counter, okCounter);
            }
            return  false;
        }
        return false;
    }

    public void setInstances(List<Instance> instances) {
        this.instances = instances;
    }

    public List<Instance> getInstances() {
        return instances;
    }
}
