package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import org.apache.commons.lang.ArrayUtils;
import com.elasticbox.jenkins.model.services.deployment.DeploymentValidationResult;
import org.apache.commons.lang.StringUtils;
import java.util.*;

/**
 * Created by serna on 11/30/15.
 */
public abstract class AbstractDeploymentTypeHandler implements DeploymentTypeHandler {


    List<PolicyBox> matchRequirementsVsClaims(List<PolicyBox> policyBoxes, AbstractBox boxToDeploy){


        if (ClaimsVsRequirementsDeployable.class.isAssignableFrom(boxToDeploy.getClass())) {

            ClaimsVsRequirementsDeployable box = (ClaimsVsRequirementsDeployable)boxToDeploy;

            final String[] requirements = box.getRequirements();
            if (ArrayUtils.isNotEmpty(requirements)) {

                List<PolicyBox> filtered = new ArrayList<>();

                final ListIterator<PolicyBox> policyBoxListIterator = policyBoxes.listIterator();
                while(policyBoxListIterator.hasNext()){
                    final PolicyBox policyBox = policyBoxListIterator.next();

                    final String[] claims = policyBox.getClaims();
                    if (ArrayUtils.isEmpty(claims)) {
                        continue;
                    }

                    Set<String> providedServices = new HashSet(Arrays.asList(claims));
                    if (providedServices.containsAll(Arrays.asList(requirements))) {
                        filtered.add(policyBox);
                    }
                }

                return filtered;
            }
        }

        return policyBoxes;
    }

    @Override
    public boolean canValidate(String deploymentTypeId) {
        return getId().equals(deploymentTypeId);
    }

    @Override
    public DeploymentValidationResult validateDeploymentData(DeployBox deployData) {
        final DeploymentValidationResult ok = new DeploymentValidationResult() {
            @Override
            public boolean isOk() {
                return true;
            }

            @Override
            public List<String> messages() {
                return new ArrayList<>();
            }
        };

        if(StringUtils.isNotEmpty(deployData.getProfile()) || StringUtils.isNotEmpty(deployData.getClaims())){
            return ok;
        }

        return new DeploymentValidationResult() {
            @Override
            public boolean isOk() {
                return false;
            }

            @Override
            public List<String> messages() {
                return new ArrayList<String>(){{add(Constants.AT_LEAST_SELECT_POLICY_OR_REQUIREMENTS);}};
            }
        };
    }


}
