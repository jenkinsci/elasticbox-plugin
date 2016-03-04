package com.elasticbox.jenkins.model.services.deployment.configuration.policies;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;

import java.util.List;

public class PolicyDeploymentDataPolicies extends AbstractDeploymentDataPoliciesHandler {


    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(
            BoxRepository boxRepository, String workspace, final AbstractBox boxToDeploy) throws RepositoryException {

        List<PolicyBox> noCloudFormationPolicyBoxes = boxRepository.getNoCloudFormationPolicyBoxes(workspace);
        return matchRequirementsVsClaims(noCloudFormationPolicyBoxes, boxToDeploy);

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        return true;
    }


}
