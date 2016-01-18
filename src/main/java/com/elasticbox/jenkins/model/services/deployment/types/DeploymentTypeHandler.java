package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.DeploymentValidationResult;

import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public interface DeploymentTypeHandler {

    boolean canManage(AbstractBox boxToDeploy);

    List<PolicyBox> retrievePoliciesToDeploy(BoxRepository boxRepository, String workspace, AbstractBox boxToDeploy) throws RepositoryException;

    DeploymentValidationResult validateDeploymentData(DeployBox deployData);

    DeploymentType getType();

}
