package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.DeploymentValidationResult;

import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class CloudFormationTemplateDeploymentTypeHandler extends AbstractDeploymentTypeHandler {

    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(BoxRepository boxRepository, String workspace, AbstractBox boxToDeploy) throws RepositoryException {

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

    @Override
    public String getId() {
        return "CloudFormationTemplate";
    }

    @Override
    public DeploymentValidationResult validateDeploymentData(DeployBox deployData) {
        return super.validateDeploymentData(deployData);
    }

}
