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
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.InstanceRepository;
import com.elasticbox.jenkins.model.repository.api.criteria.box.CloudFormationPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.criteria.box.NoCloudFormationPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.criteria.box.NoPolicyAndNoApplicationBoxes;
import com.elasticbox.jenkins.model.repository.api.criteria.box.NoPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.factory.box.GenericBoxFactory;
import com.elasticbox.jenkins.model.repository.api.factory.instance.InstanceFactoryImpl;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 11/26/15.
 */
    public class InstanceRepositoryAPIImpl implements InstanceRepository {

    private static final Logger logger = Logger.getLogger(InstanceRepositoryAPIImpl.class.getName());

    private APIClient client;

    public InstanceRepositoryAPIImpl(APIClient client) {
        this.client = client;
    }

    @Override
    public Instance getInstance(String instanceId) throws RepositoryException {
        try{
            JSONObject jsonObject = client.getInstance(instanceId);
            return new InstanceFactoryImpl().create(jsonObject);
        } catch (IOException e) {
            logger.log(Level.SEVERE,"There is an error retrieving instance: "+instanceId+" from the API",e);
            throw new RepositoryException("Error retrieving instance: "+instanceId+" from API");
        } catch (ElasticBoxModelException e) {
            logger.log(Level.SEVERE, "Error converting instance: "+instanceId+" from JSON", e);
            throw new RepositoryException("Error converting instance: "+instanceId+" from JSON");
        }
    }

    @Override
    public List<Instance> getInstances(String workspace, String[] id) throws RepositoryException {

        try {
            List<Instance> instances = new ArrayList<>();
            final InstanceFactoryImpl instanceFactory = new InstanceFactoryImpl();
            final JSONArray instancesJSONArray = client.getInstances(workspace, Arrays.asList(id));
            for (Object jsonElement : instancesJSONArray) {
                JSONObject jsonInstance = (JSONObject) jsonElement;
                final Instance instance = instanceFactory.create(jsonInstance);
                instances.add(instance);
            }
            return instances;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving instances: " + Arrays.asList(id) + " for workspace: "+workspace, e);
            throw new RepositoryException("There is an error retrieving instances: " + Arrays.asList(id) + " for workspace: "+workspace);
        }
    }
}
