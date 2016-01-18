package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class DeploymentTypeDirector {

    private BoxRepository boxRepository;

    private DeploymentType [] deploymentTypes;

    public DeploymentTypeDirector(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;

        deploymentTypes = new DeploymentType[]{
                new CloudFormationManagedDeploymentType(boxRepository),
                new CloudFormationTemplateDeploymentType(boxRepository),
                new DefaultDeploymentType(boxRepository)
        };

    }

    public List<PolicyBox> getPolicies(String workspace, AbstractBox box) throws ServiceException {
        try {
            for (DeploymentType deploymentType : deploymentTypes) {
                if (deploymentType.canManage(box)){
                    return deploymentType.calculatePolicies(workspace, box);
                }
            }
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        throw new ServiceException("There is no DeploymentType to calculate policies for workspace: "+workspace+", boxToDeploy: "+ box.getId());
    }

}
