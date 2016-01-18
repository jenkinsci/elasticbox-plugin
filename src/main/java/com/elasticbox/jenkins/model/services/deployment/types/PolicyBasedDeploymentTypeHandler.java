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
public class PolicyBasedDeploymentTypeHandler extends AbstractDeploymentTypeHandler {


    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(BoxRepository boxRepository, String workspace, final AbstractBox boxToDeploy) throws RepositoryException {

        List<PolicyBox> noCloudFormationPolicyBoxes = boxRepository.getNoCloudFormationPolicyBoxes(workspace);
        return matchRequirementsVsClaims(noCloudFormationPolicyBoxes, boxToDeploy);

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        return true;
    }

    @Override
    public String getId() {
        return "Policy";
    }

    @Override
    public DeploymentValidationResult validateDeploymentData(DeployBox deployData) {
        return super.validateDeploymentData(deployData);
    }

}
