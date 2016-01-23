/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.services.deployment.configuration.validation;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class CloudFormationManagedAbstractDeploymentDataValidator implements DeploymentDataTypeValidator {

    @Override
    public DeploymentType getManagedType() {
        return DeploymentType.CLOUDFORMATIONMANAGED_DEPLOYMENT_TYPE;
    }

    @Override
    public DeploymentValidationResult validateDeploymentDataType(DeployBox deployData) {

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
