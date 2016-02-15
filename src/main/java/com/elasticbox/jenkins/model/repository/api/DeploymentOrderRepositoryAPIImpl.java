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

package com.elasticbox.jenkins.model.repository.api;

import com.elasticbox.APIClient;
import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.DeploymentOrderRepository;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.instances.InstanceTransformer;
import com.elasticbox.jenkins.model.repository.api.serializer.deployment.ApplicationBoxDeploymentSerializer;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.execution.context.ApplicationBoxDeploymentContext;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 1/20/16.
 */
public class DeploymentOrderRepositoryAPIImpl implements DeploymentOrderRepository{

    private static final Logger logger = Logger.getLogger(DeploymentOrderRepositoryAPIImpl.class.getName());

    private APIClient client;

    public DeploymentOrderRepositoryAPIImpl(APIClient client) {
        this.client = client;
    }

    @Override
    public List<Instance> deploy(ApplicationBoxDeploymentContext deploymentContext) throws RepositoryException{

        try {
            final JSONObject request = new ApplicationBoxDeploymentSerializer().createRequest(deploymentContext);
            List<Instance> instances = new ArrayList<>();
            final JSONArray instancesJSONArray = client.<JSONArray>doPost(Constants.INSTANCES_API_RESOURCE, request, true);
            for (Object jsonElement : instancesJSONArray) {
                JSONObject jsonInstance = (JSONObject) jsonElement;
                final Instance instance =  new InstanceTransformer().apply(jsonInstance);
                instances.add(instance);
            }
            return instances;
        } catch (IOException e) {
            deploymentContext.getLogger().error("There is an error deploying ApplicationBox: {0}, id: {1}",
                    deploymentContext.getOrder().getName(),
                        deploymentContext.getOrder().getBox());

            logger.log(Level.SEVERE, "There is an error deploying ApplicationBox, order: "+deploymentContext,e);
            throw new RepositoryException("There is an error deploying ApplicationBox, order: "+deploymentContext);
        }
    }
}
