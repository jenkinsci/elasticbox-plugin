package com.elasticbox.jenkins.model.services.deployment.configuration.policies;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;

import java.util.List;

public class CloudFormationTemplateDeploymentDataPolicies extends AbstractDeploymentDataPoliciesHandler {


    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(
            BoxRepository boxRepository, String workspace, AbstractBox boxToDeploy) throws RepositoryException {

        List<PolicyBox> cloudFormationPolicyBoxes = boxRepository.getCloudFormationPolicyBoxes(workspace);

        return matchRequirementsVsClaims(cloudFormationPolicyBoxes, boxToDeploy);

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        if (boxToDeploy.getType() == BoxType.CLOUDFORMATION) {
            CloudFormationBox cloudFormationBox = (CloudFormationBox)boxToDeploy;
            return cloudFormationBox.getCloudFormationType() == CloudFormationBoxType.TEMPLATE;
        }

        return false;
    }

}
