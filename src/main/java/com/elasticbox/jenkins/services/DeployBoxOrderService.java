package com.elasticbox.jenkins.services;

import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.repository.error.RepositoryException;
import com.elasticbox.jenkins.services.error.ServiceException;

import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public interface DeployBoxOrderService {

    public DeployBoxOrderResult<List<PolicyBox>> deploymentOptions(String workspace, String boxToDeploy) throws RepositoryException, ServiceException;

}
