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

import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.model.services.deployment.execution.context.AbstractBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.deployment.execution.context.ApplicationBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.util.TaskLogger;

import java.util.EnumMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BoxDeployerFactory<R extends BoxDeployer, T extends AbstractBoxDeploymentContext> {

    private static final Logger logger = Logger.getLogger(BoxDeployerFactory.class.getName());

    protected static EnumMap<DeploymentType, BoxDeployerFactory> deploymentTypeMap =
            new EnumMap<DeploymentType, BoxDeployerFactory>(DeploymentType.class);

    public abstract R createDeployer(T context) ;

    static {
        deploymentTypeMap.put(
                DeploymentType.APPLICATIONBOX_DEPLOYMENT_TYPE,
                new ApplicationBoxDeployer.ApplicationBoxDeployerFactory());
    }


    public static <R extends BoxDeployer, T extends AbstractBoxDeploymentContext> R createBoxDeployer(T context)
            throws ServiceException {

        final DeploymentType deploymentType = context.getDeploymentType();
        final TaskLogger taskLogger = context.getLogger();

        if (deploymentTypeMap.containsKey(deploymentType)) {
            final BoxDeployer boxDeployer = deploymentTypeMap.get(deploymentType).createDeployer(context);
            return (R) boxDeployer;
        }

        taskLogger.error("There is no BoxDeployer for DeploymentType: {0}", deploymentType.getValue());
        BoxDeployerFactory.logger.log(
                Level.SEVERE, "There is no BoxDeployer for DeploymentType: " + deploymentType.getValue());

        throw new ServiceException("There is no BoxDeployer for DeploymentType: " + deploymentType.getValue());

    }

}
