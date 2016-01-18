package com.elasticbox.jenkins.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.repository.BoxRepository;
import org.apache.commons.lang.ArrayUtils;

import java.util.*;

/**
 * Created by serna on 11/30/15.
 */
public abstract class AbstractDeploymentType implements DeploymentType{

    protected BoxRepository boxRepository;

    public AbstractDeploymentType(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

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

}
