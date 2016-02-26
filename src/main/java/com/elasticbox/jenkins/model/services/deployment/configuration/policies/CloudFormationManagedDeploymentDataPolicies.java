package com.elasticbox.jenkins.model.services.deployment.configuration.policies;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CloudFormationManagedDeploymentDataPolicies extends AbstractDeploymentDataPoliciesHandler {

    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(
            BoxRepository boxRepository, String workspace, final AbstractBox boxToDeploy) throws RepositoryException {

        return Arrays.asList((PolicyBox)boxToDeploy);

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        if (boxToDeploy.getType() == BoxType.CLOUDFORMATION) {
            CloudFormationBox cloudFormationBox = (CloudFormationBox)boxToDeploy;
            return cloudFormationBox.getCloudFormationType() == CloudFormationBoxType.MANAGED;
        }

        return false;
    }



}
