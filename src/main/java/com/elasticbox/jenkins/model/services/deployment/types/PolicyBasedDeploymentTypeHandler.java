package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.DeploymentValidationResult;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by serna on 11/30/15.
 */
public class PolicyBasedDeploymentTypeHandler extends AbstractDeploymentTypeHandler {


    public PolicyBasedDeploymentTypeHandler() {
        super(DeploymentType.SCRIPTBOX_DEPLOYMENT_TYPE);
    }

    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(BoxRepository boxRepository, String workspace, final AbstractBox boxToDeploy) throws RepositoryException {

        List<PolicyBox> noCloudFormationPolicyBoxes = boxRepository.getNoCloudFormationPolicyBoxes(workspace);
        return matchRequirementsVsClaims(noCloudFormationPolicyBoxes, boxToDeploy);

    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        return true;
    }

    @Override
    public DeploymentValidationResult validateDeploymentData(DeployBox deployData) {
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
                return new ArrayList<Cause>() {{
                    add(new Cause() {
                        @Override
                        public String message() {
                            return Constants.AT_LEAST_SELECT_POLICY_OR_REQUIREMENTS;
                        }

                        @Override
                        public String field() {
                            return "profile";
                        }
                    });
                }};
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
