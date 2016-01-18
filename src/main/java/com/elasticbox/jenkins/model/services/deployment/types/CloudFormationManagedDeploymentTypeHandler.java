package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.DeploymentValidationResult;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class CloudFormationManagedDeploymentTypeHandler extends AbstractDeploymentTypeHandler {

    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(BoxRepository boxRepository, String workspace, final AbstractBox boxToDeploy) throws RepositoryException {

        return new ArrayList<PolicyBox>(){{add((ManagedCloudFormationBox)boxToDeploy);}};

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        if (boxToDeploy.getType() == BoxType.CLOUDFORMATION) {
            CloudFormationBox cloudFormationBox = (CloudFormationBox)boxToDeploy;
            return cloudFormationBox.getCloudFormationType() == CloudFormationBoxType.MANAGED;
        }

        return false;
    }

    @Override
    public String getId() {
        return "CloudFormationManaged";
    }

    @Override
    public DeploymentValidationResult validateDeploymentData(DeployBox deployData) {
        if (StringUtils.isEmpty(deployData.getProvider()) || StringUtils.isEmpty(deployData.getLocation())) {
            return new DeploymentValidationResult() {
                @Override
                public boolean isOk() {
                    return false;
                }

                @Override
                public List<String> messages() {
                    return new ArrayList<String>(){{add(Constants.PROVIDER_AND_LOCATION_SHOULD_BE_PROVIDED);}};
                }
            };
        }

        return new DeploymentValidationResult() {
            @Override
            public boolean isOk() {
                return true;
            }

            @Override
            public List<String> messages() {
                return new ArrayList<>();
            }
        };
    }

}
