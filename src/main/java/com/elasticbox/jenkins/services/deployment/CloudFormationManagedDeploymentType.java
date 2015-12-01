package com.elasticbox.jenkins.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.repository.BoxRepository;
import com.elasticbox.jenkins.repository.error.RepositoryException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class CloudFormationManagedDeploymentType extends AbstractDeploymentType {


    public CloudFormationManagedDeploymentType(BoxRepository boxRepository) {
        super(boxRepository);
    }

    @Override
    public List<PolicyBox> calculatePolicies(String workspace, final AbstractBox boxToDeploy) throws RepositoryException {

        return new ArrayList<PolicyBox>(){{add((ManagedCloudFormationBox)boxToDeploy);}};

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        if (boxToDeploy.isCloudFormationBox()) {
            CloudFormationBox cloudFormationBox = (CloudFormationBox)boxToDeploy;
            return cloudFormationBox.isManagedCloudFormationBox();
        }

        return false;
    }

}
