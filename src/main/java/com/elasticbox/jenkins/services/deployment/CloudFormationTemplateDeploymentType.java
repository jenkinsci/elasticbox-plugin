package com.elasticbox.jenkins.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.repository.BoxRepository;
import com.elasticbox.jenkins.repository.error.RepositoryException;

import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class CloudFormationTemplateDeploymentType extends AbstractDeploymentType {


    public CloudFormationTemplateDeploymentType(BoxRepository boxRepository) {
        super(boxRepository);
    }

    @Override
    public List<PolicyBox> calculatePolicies(String workspace, AbstractBox boxToDeploy) throws RepositoryException {

        List<PolicyBox> cloudFormationPolicyBoxes = boxRepository.getCloudFormationPolicyBoxes(workspace);
        return matchRequirementsVsClaims(cloudFormationPolicyBoxes, boxToDeploy);

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        if (boxToDeploy.isCloudFormationBox()) {
            CloudFormationBox cloudFormationBox = (CloudFormationBox)boxToDeploy;
            return cloudFormationBox.isTemplateCloudFormationBox();
        }

        return false;
    }

}
