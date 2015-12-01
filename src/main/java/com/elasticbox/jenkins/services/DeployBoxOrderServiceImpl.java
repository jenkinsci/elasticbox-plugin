package com.elasticbox.jenkins.services;

import com.elasticbox.jenkins.model.box.*;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.box.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.repository.BoxRepository;
import com.elasticbox.jenkins.repository.api.BoxRepositoryAPIImpl;
import com.elasticbox.jenkins.repository.error.RepositoryException;
import com.elasticbox.jenkins.services.deployment.DeploymentDirector;
import com.elasticbox.jenkins.services.error.ServiceException;

import java.util.ArrayList;
import java.util.List;

/**
 * This is how we decided to encapsulate the business logic. So, this service model the deploy box
 * user case.
 */
public class DeployBoxOrderServiceImpl implements DeployBoxOrderService {

    private BoxRepository boxRepository;

    public DeployBoxOrderServiceImpl(String cloudName) {
        this.boxRepository = new BoxRepositoryAPIImpl(cloudName);
    }

    public DeployBoxOrderResult<List<PolicyBox>> deploymentOptions(String workspace, String boxToDeploy) throws ServiceException {

        try {
            final AbstractBox box = boxRepository.getBox(boxToDeploy);
            final List<PolicyBox> policies = new DeploymentDirector(boxRepository).getPolicies(workspace, box);

            return new DeployBoxOrderResult<List<PolicyBox>>(policies);

        } catch (RepositoryException e) {
            e.printStackTrace();
            throw new ServiceException("Impossible to get policies for workspace: "+workspace+", box: "+boxToDeploy);
        }

    }


}
