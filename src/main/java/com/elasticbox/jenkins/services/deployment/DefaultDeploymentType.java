package com.elasticbox.jenkins.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.repository.BoxRepository;
import com.elasticbox.jenkins.repository.error.RepositoryException;

import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class DefaultDeploymentType extends AbstractDeploymentType {


    public DefaultDeploymentType(BoxRepository boxRepository) {
        super(boxRepository);
    }

    @Override
    public List<PolicyBox> calculatePolicies(String workspace, final AbstractBox boxToDeploy) throws RepositoryException {

        List<PolicyBox> noCloudFormationPolicyBoxes = boxRepository.getNoCloudFormationPolicyBoxes(workspace);
        return matchRequirementsVsClaims(noCloudFormationPolicyBoxes, boxToDeploy);

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        return true;
    }

}
