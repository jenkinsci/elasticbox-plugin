package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.DeploymentValidationResult;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class DeploymentTypeDirector {

    private DeploymentTypeHandler[] deploymentTypeHandlers;

    public DeploymentTypeDirector() {

        deploymentTypeHandlers = new DeploymentTypeHandler[]{
                new CloudFormationManagedDeploymentTypeHandler(),
                new CloudFormationTemplateDeploymentTypeHandler(),
                new ApplicationBoxDeploymentTypeHandler(),
                new PolicyBasedDeploymentTypeHandler()
        };

    }

    public List<PolicyBox> getPolicies(BoxRepository boxRepository, String workspace, AbstractBox box) throws ServiceException {
        try {
            for (DeploymentTypeHandler deploymentTypeHandler : deploymentTypeHandlers) {
                if (deploymentTypeHandler.canManage(box)){
                    return deploymentTypeHandler.retrievePoliciesToDeploy(boxRepository, workspace, box);
                }
            }
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        throw new ServiceException("There is no DeploymentTypeHandler to calculate policies for workspace: "+workspace+", boxToDeploy: "+ box.getId());
    }

    public DeploymentTypeHandler getDeploymentType(final AbstractBox box) throws ServiceException {

        final DeploymentTypeHandler deploymentTypeHandler = firstMatch(new DeploymentTypeCondition() {
            @Override
            public boolean comply(DeploymentTypeHandler handler) {
                return handler.canManage(box);
            }
        });

        return deploymentTypeHandler;
    }

    public DeploymentValidationResult validateDeploymentData(final DeployBox deployData) throws ServiceException {

        final DeploymentTypeHandler deploymentTypeHandler = firstMatch(new DeploymentTypeCondition() {
            @Override
            public boolean comply(DeploymentTypeHandler handler) {
                return handler.getManagedType().getId().equals(deployData.getBoxDeploymentType());
            }
        });

        return deploymentTypeHandler.validateDeploymentData(deployData);
    }

    private DeploymentTypeHandler firstMatch(DeploymentTypeCondition condition) throws ServiceException {
        for (DeploymentTypeHandler deploymentTypeHandler : deploymentTypeHandlers) {
            if (condition.comply(deploymentTypeHandler)){
                return deploymentTypeHandler;
            }
        }
        throw new ServiceException("There is no DeploymentTypeHandler for this criteria");

    }


    interface DeploymentTypeCondition{
            boolean comply(DeploymentTypeHandler handler);
    }

}
