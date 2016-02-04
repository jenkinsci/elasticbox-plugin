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

package com.elasticbox.jenkins.model.services.deployment.execution.deployers;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.execution.context.ApplicationBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.deployment.execution.order.ApplicationBoxDeploymentOrder;
import com.elasticbox.jenkins.model.services.deployment.execution.task.CheckInstancesDeployedTask;
import com.elasticbox.jenkins.model.services.deployment.execution.task.DeployApplicationBoxTask;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.model.services.task.TaskException;
import com.elasticbox.jenkins.util.TaskLogger;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 1/19/16.
 */
public class ApplicationBoxDeployer implements BoxDeployer<ApplicationBoxDeploymentContext>{

    private static final Logger logger = Logger.getLogger(ApplicationBoxDeployer.class.getName());

    public List<Instance> deploy(ApplicationBoxDeploymentContext context) throws RepositoryException {

        final TaskLogger deploymentLogger = context.getLogger();
        final BoxRepository boxRepository = context.getBoxRepository();

        final ApplicationBoxDeploymentOrder order = context.getOrder();
        final String box = order.getBox();
        final String boxVersion = order.getBoxVersion();
        final String owner = order.getOwner();

        //Calculate the final box id to deploy taking acount of the version and box id
        if(Constants.LATEST_BOX_VERSION.equals(boxVersion)){
            final AbstractBox boxModel = boxRepository.getBox(box);
            if(!boxModel.canWrite(owner)){
                final List<AbstractBox> boxVersions = boxRepository.getBoxVersions(box);
                if(!boxVersion.isEmpty()){
                    context.setBoxToDeployId(boxVersions.get(0).getId());
                }
            }
        }

        if (!order.isWaitForDone()){
            return context.getDeploymentOrderRepository().deploy(context);
        }

        final DeployApplicationBoxTask deployApplicationBoxTask = new DeployApplicationBoxTask.Builder()
                .withApplicationBoxDeploymentContext(context)
                .withDependingTask(new CheckInstancesDeployedTask(context))
                .withTimeout(Constants.DEFAULT_DEPLOYMENT_APPLICATION_BOX_TIMEOUT)
                .build();

        try {
            deployApplicationBoxTask.execute();
            return deployApplicationBoxTask.succesfullyDeployedInstances();
        } catch (TaskException e) {
            deploymentLogger.error("ApplicationBox: {0} deployment error", order.getName());
            logger.log(Level.SEVERE, "ApplicationBox deployment error, order: "+context.getOrder(), e);
            throw new ServiceException("ApplicationBox deployment error, order: "+context.getOrder(), e);
        }
    }

    public static class ApplicationBoxDeployerFactory extends BoxDeployerFactory<ApplicationBoxDeployer, ApplicationBoxDeploymentContext>{

        @Override
        public ApplicationBoxDeployer createDeployer(ApplicationBoxDeploymentContext context) {
            return new ApplicationBoxDeployer();
        }
    }
}
