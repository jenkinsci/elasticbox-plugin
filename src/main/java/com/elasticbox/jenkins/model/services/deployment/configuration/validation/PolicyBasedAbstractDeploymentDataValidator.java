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

public class PolicyBasedAbstractDeploymentDataValidator implements DeploymentDataTypeValidator {

    @Override
    public DeploymentType getManagedType() {
        return DeploymentType.SCRIPTBOX_DEPLOYMENT_TYPE;
    }

    @Override
    public DeploymentValidationResult validateDeploymentDataType(DeployBox deployData) {
        final String profile = deployData.getProfile();
        final String claims = deployData.getClaims();
        final DeploymentValidationResult ok = new DeploymentValidationResult() {
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
                final Set<String> claimsSet = new HashSet<String>();
                if (StringUtils.isNotEmpty(claims)) {
                    for (String tag : claims.split(",")) {
                        claimsSet.add(tag.trim());
                    }
                }
                return new CloudformationTemplateDeploymentData(profile, claimsSet);
            }
        };

        if (StringUtils.isNotEmpty(profile) || StringUtils.isNotEmpty(claims)) {
            return ok;
        }

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
                        return Constants.AT_LEAST_SELECT_POLICY_OR_REQUIREMENTS;
                    }

                    @Override
                    public String field() {
                        return "profile";
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

    private class CloudformationTemplateDeploymentData implements DeploymentValidationResult.DeploymentData {

        private DeploymentType deploymentType = DeploymentType.SCRIPTBOX_DEPLOYMENT_TYPE;
        private String policy;
        private Set<String> claims;

        public CloudformationTemplateDeploymentData(String policy, Set<String> claims) {
            this.policy = policy;
            this.claims = claims;
        }

        public DeploymentType getDeploymentType() {
            return deploymentType;
        }

        public String getPolicy() {
            return policy;
        }

        public Set<String> getClaims() {
            return claims;
        }
    }

}
