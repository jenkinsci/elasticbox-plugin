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

package com.elasticbox.jenkins.model.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.services.deployment.configuration.policies.DeploymentDataPoliciesHandler;
import com.elasticbox.jenkins.model.services.deployment.execution.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;

import java.util.List;

/**
 * Created by serna on 1/13/16.
 */
public interface DeployBoxOrderService {

    DeployBoxOrderResult<List<AbstractBox>> updateableBoxes(String workspace) throws ServiceException;

    DeploymentType deploymentType(String boxToDeploy) throws ServiceException;

    DeployBoxOrderResult<List<PolicyBox>> deploymentPolicies(String workspace, String boxToDeploy) throws ServiceException;

    DeployBoxOrderResult<AbstractWorkspace> findWorkspaceOrFirstByDefault(String workspace) throws ServiceException;

    DeployBoxOrderResult<List<AbstractWorkspace>> getWorkspaces()throws ServiceException;;

    DeployBoxOrderResult<List<AbstractBox>> getBoxesToDeploy(String workspace) throws ServiceException;

}
