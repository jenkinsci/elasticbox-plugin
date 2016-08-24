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
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApplicationBoxAbstractDeploymentDataValidator implements DeploymentDataTypeValidator {

    @Override
    public DeploymentType getManagedType() {
        return DeploymentType.APPLICATIONBOX_DEPLOYMENT_TYPE;
    }

    @Override
    public DeploymentValidationResult validateDeploymentDataType(DeployBox deployData) {

        if (StringUtils.isEmpty(deployData.getInstanceName() )) {
            return new DeploymentValidationResult() {
                @Override
                public boolean isOk() {
                    return false;
                }

                @Override
                public List<Cause> causes() {

                    final Cause cause = new Cause() {
                        @Override
                        public String message() {
                            return Constants.INSTANCE_SHOULD_BE_PROVIDED;
                        }

                        @Override
                        public String field() {
                            return "instanceName";
                        }
                    };

                    return Collections.singletonList(cause);
                }

                @Override
                public DeploymentData getDeploymentData() {
                    return null;
                }
            };
        }

        final String claims = deployData.getClaims();
        final Set<String> claimsSet = new HashSet<String>();
        if (StringUtils.isNotEmpty(claims)) {
            for (String tag : claims.split(",")) {
                claimsSet.add(tag.trim());
            }
        }

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
                return new ApplicationBoxDeploymentData(claimsSet);
            }
        };
    }

    private class ApplicationBoxDeploymentData implements DeploymentValidationResult.DeploymentData {

        private DeploymentType deploymentType = DeploymentType.APPLICATIONBOX_DEPLOYMENT_TYPE;
        private Set<String> requirements;

        public ApplicationBoxDeploymentData(Set<String> requirements) {
            this.requirements = requirements;
        }

        public DeploymentType getDeploymentType() {
            return deploymentType;
        }

        public Set<String> getRequirements() {
            return requirements;
        }
    }


}
