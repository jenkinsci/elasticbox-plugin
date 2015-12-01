package com.elasticbox.jenkins.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.repository.error.RepositoryException;

import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public interface DeploymentType {

    List<PolicyBox> calculatePolicies(String workspace, AbstractBox boxToDeploy) throws RepositoryException;

    boolean canManage(AbstractBox boxToDeploy);


}
