package com.elasticbox.jenkins.model.services.deployment.configuration.policies;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.error.ServiceException;

import org.apache.commons.lang.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public abstract class AbstractDeploymentDataPoliciesHandler implements DeploymentDataPoliciesHandler {

    private static DeploymentDataPoliciesHandler[] deploymentTypeHandlers = new DeploymentDataPoliciesHandler[] {
        new CloudFormationManagedDeploymentDataPolicies(),
        new CloudFormationTemplateDeploymentDataPolicies(),
        new ApplicationBoxDeploymentDataPolicies(),
        new PolicyDeploymentDataPolicies()
    };


    public static List<PolicyBox> getPolicies(BoxRepository boxRepository, String workspace, AbstractBox box) throws
            ServiceException {
        try {
            for (DeploymentDataPoliciesHandler deploymentTypeHandler : deploymentTypeHandlers) {
                if (deploymentTypeHandler.canManage(box)) {
                    return deploymentTypeHandler.retrievePoliciesToDeploy(boxRepository, workspace, box);
                }
            }
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        throw new ServiceException("There is no DeploymentTypeHandler to calculate policies for workspace: "
                + workspace + ", boxToDeploy: " + box.getId());
    }

    public static DeploymentDataPoliciesHandler getDeploymentType(final AbstractBox box) throws ServiceException {

        final DeploymentDataPoliciesHandler deploymentTypeHandler = firstMatch(new DeploymentTypeCondition() {
            @Override
            public boolean comply(DeploymentDataPoliciesHandler handler) {
                return handler.canManage(box);
            }
        });

        return deploymentTypeHandler;
    }


    private static DeploymentDataPoliciesHandler firstMatch(DeploymentTypeCondition condition) throws ServiceException {
        for (DeploymentDataPoliciesHandler deploymentTypeHandler : deploymentTypeHandlers) {
            if (condition.comply(deploymentTypeHandler)) {
                return deploymentTypeHandler;
            }
        }
        throw new ServiceException("There is no DeploymentTypeHandler for this criteria");

    }

    protected List<PolicyBox> matchRequirementsVsClaims(List<PolicyBox> policyBoxes, AbstractBox boxToDeploy) {


        if (ClaimsVsRequirementsDeployable.class.isAssignableFrom(boxToDeploy.getClass())) {

            ClaimsVsRequirementsDeployable box = (ClaimsVsRequirementsDeployable) boxToDeploy;

            final String[] requirements = box.getRequirements();
            if (ArrayUtils.isNotEmpty(requirements)) {

                List<PolicyBox> filtered = new ArrayList<>();

                final ListIterator<PolicyBox> policyBoxListIterator = policyBoxes.listIterator();
                while (policyBoxListIterator.hasNext()) {
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

    interface DeploymentTypeCondition {
        boolean comply(DeploymentDataPoliciesHandler handler);
    }

}
