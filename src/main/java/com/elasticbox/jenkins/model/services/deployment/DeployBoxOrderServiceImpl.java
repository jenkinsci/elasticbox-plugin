package com.elasticbox.jenkins.model.services.deployment;

import com.elasticbox.APIClient;
import com.elasticbox.jenkins.model.box.*;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.DeploymentOrderRepository;
import com.elasticbox.jenkins.model.repository.InstanceRepository;
import com.elasticbox.jenkins.model.repository.api.BoxRepositoryAPIImpl;
import com.elasticbox.jenkins.model.repository.api.DeploymentOrderRepositoryAPIImpl;
import com.elasticbox.jenkins.model.repository.api.InstanceRepositoryAPIImpl;
import com.elasticbox.jenkins.model.services.deployment.configuration.policies.AbstractDeploymentDataPoliciesHandler;
import com.elasticbox.jenkins.model.services.deployment.execution.context.AbstractBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.deployment.execution.deployers.BoxDeployer;
import com.elasticbox.jenkins.model.services.deployment.execution.deployers.BoxDeployerFactory;
import com.elasticbox.jenkins.model.services.deployment.execution.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.error.ServiceException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is how we decided to encapsulate the business logic. So, this service model the deploy box
 * user case.
 */
public class DeployBoxOrderServiceImpl implements DeployBoxOrderService {

    private static final Logger logger = Logger.getLogger(DeployBoxOrderServiceImpl.class.getName());

    private final InstanceRepository instanceRepository;
    private final DeploymentOrderRepository deploymentOrderRepository;
    private final BoxRepository boxRepository;

    public DeployBoxOrderServiceImpl(APIClient client) {

        this.boxRepository = new BoxRepositoryAPIImpl(client);
        this.instanceRepository = new InstanceRepositoryAPIImpl(client);
        this.deploymentOrderRepository =  new DeploymentOrderRepositoryAPIImpl(client);
    }

    @Override
    public DeploymentType deploymentType(String boxToDeploy) throws ServiceException {

        try {
            final AbstractBox box = boxRepository.getBox(boxToDeploy);
            final DeploymentType type = DeploymentType.findBy(box);
            return type;

        } catch (RepositoryException e) {
            logger.log(Level.SEVERE, "\"Impossible to retrieve box: \"+boxToDeploy");
            throw new ServiceException("Impossible to retrieve box: "+boxToDeploy);
        }
    }

    @Override
    public DeployBoxOrderResult<List<AbstractBox>> updateableBoxes(String workspace) throws ServiceException {

        try {
            final List<AbstractBox> updateableBoxes = boxRepository.getNoPolicyAndNoApplicationBoxes(workspace);
            return new DeployBoxOrderResult<List<AbstractBox>>(updateableBoxes);

        } catch (RepositoryException e) {
            logger.log(Level.SEVERE, "Impossible to retrieve updateable boxes (no policies neither application boxes) for workspace: "+workspace);
            throw new ServiceException("Impossible to retrieve updateable boxes (no policies neither application boxes) for workspace: "+workspace);
        }
    }


    @Override
    public DeployBoxOrderResult<List<PolicyBox>> deploymentPolicies(String workspace, String boxToDeploy) throws ServiceException {

        try {
            final AbstractBox box = boxRepository.getBox(boxToDeploy);
            final List<PolicyBox> policies = AbstractDeploymentDataPoliciesHandler.getPolicies(boxRepository, workspace, box);
            return new DeployBoxOrderResult<List<PolicyBox>>(policies);

        } catch (RepositoryException e) {
            logger.log(Level.SEVERE, "Impossible to get policies for workspace: "+workspace+", box: "+boxToDeploy);
            throw new ServiceException("Impossible to get policies for workspace: "+workspace+", box: "+boxToDeploy);
        }

    }

    public <T extends AbstractBoxDeploymentContext>DeployBoxOrderResult<List<Instance>> deploy(T context) throws ServiceException{

        context.setBoxRepository(boxRepository);
        context.setDeploymentOrderRepository(deploymentOrderRepository);
        context.setInstanceRepository(instanceRepository);

        final BoxDeployer boxDeployer = BoxDeployerFactory.createBoxDeployer(context);
        try {
            context.setBoxRepository(boxRepository);

            final List<Instance> instancesDeployed = boxDeployer.deploy(context);
            return  new DeployBoxOrderResult<List<Instance>>(instancesDeployed);

        } catch (RepositoryException e) {
            logger.log(Level.SEVERE, "Deployment error, deploy order: " + context.getOrder());
            throw new ServiceException("Deployment error, deploy order: " + context.getOrder());
        }
    }

}
