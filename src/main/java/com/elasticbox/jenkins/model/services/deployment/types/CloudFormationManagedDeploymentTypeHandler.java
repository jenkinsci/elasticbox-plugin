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
import java.util.Set;

/**
 * Created by serna on 11/30/15.
 */
public class CloudFormationManagedDeploymentTypeHandler extends AbstractDeploymentTypeHandler {

    public CloudFormationManagedDeploymentTypeHandler() {
        super(DeploymentType.CLOUDFORMATIONMANAGED_DEPLOYMENT_TYPE);
    }

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
    public DeploymentValidationResult validateDeploymentData(DeployBox deployData) {

        final String provider = deployData.getProvider();
        final String location = deployData.getLocation();

        if (StringUtils.isEmpty(provider) || StringUtils.isEmpty(location)) {

            final DeploymentValidationResult.Cause cause = StringUtils.isEmpty(provider) ? new DeploymentValidationResult.Cause() {
                @Override
                public String message() {
                    return Constants.PROVIDER_SHOULD_BE_PROVIDED;
                }

                @Override
                public String field() {
                    return "provider";
                }
            }: new DeploymentValidationResult.Cause() {
                @Override
                public String message() {
                    return Constants.LOCATION_SHOULD_BE_PROVIDED;
                }

                @Override
                public String field() {
                    return "location";
                }
            };

            //error case, there is no provider or location
            return new DeploymentValidationResult() {
                @Override
                public boolean isOk() {
                    return false;
                }

                @Override
                public List<Cause> causes() {
                    return new ArrayList<Cause>(){{add(cause);}};
                }

                @Override
                public DeploymentData getDeploymentData() {
                    return null;
                }
            };
        }

        //all went ok
        return new DeploymentValidationResult() {
            @Override
            public boolean isOk() {
                return true;
            }

            @Override
            public List<Cause> causes() {
                return new ArrayList<>();
            }

            @Override
            public DeploymentData getDeploymentData() {
                return new CloudformationManagedDeploymentData(provider, location);
            }
        };
    }


    private class CloudformationManagedDeploymentData implements DeploymentValidationResult.DeploymentData {

        private DeploymentType deploymentType = DeploymentType.CLOUDFORMATIONMANAGED_DEPLOYMENT_TYPE;
        private String provider;
        private String location;

        public CloudformationManagedDeploymentData(String provider, String location) {
            this.location=location;
            this.provider=provider;
        }

        public DeploymentType getDeploymentType() {
            return deploymentType;
        }

        public String getProvider() {
            return provider;
        }

        public String getLocation() {
            return location;
        }
    }

}
