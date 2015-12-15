package com.elasticbox.jenkins.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.repository.BoxRepository;
import com.elasticbox.jenkins.repository.error.RepositoryException;
import com.elasticbox.jenkins.services.error.ServiceException;
import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class DeploymentDirector {

    private BoxRepository boxRepository;

    private DeploymentType [] deploymentTypes;

    public DeploymentDirector(BoxRepository boxRepository) {
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
