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

package com.elasticbox.jenkins.model.repository.api.factory.instance;

import com.elasticbox.jenkins.builders.Operation;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.factory.JSONFactoryUtils;
import com.elasticbox.jenkins.model.repository.api.factory.ModelFactory;
import com.elasticbox.jenkins.model.repository.api.factory.box.AbstractBoxFactory;
import com.elasticbox.jenkins.model.repository.api.factory.box.GenericBoxFactory;
import com.elasticbox.jenkins.util.JsonUtil;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.util.logging.Level;

/**
 * Created by serna on 11/29/15.
 */
public class InstanceFactoryImpl implements InstanceFactory<Instance> {
    @Override
    public Instance create(JSONObject jsonObject) throws ElasticBoxModelException {

        final JSONObject operation = jsonObject.getJSONObject("operation");

        final Instance instance = new Instance.ComplexBuilder()
                .withSchema(jsonObject.getString("schema"))
                .withBox(jsonObject.getString("box"))
                .withAutomaticUpdates(Instance.AutomaticUpdates.findByValue(jsonObject.getString("automatic_updates")))
                .withBoxes(getBoxes(jsonObject.getJSONArray("boxes")))
                .withName(jsonObject.getString("name"))
                .withOperation(getOperation(jsonObject.getJSONObject("operation")))
                .withService(getService(jsonObject.getJSONObject("service")))
                .withState(Instance.State.findByValue(jsonObject.getString("state")))
                .withTags(JSONFactoryUtils.toStringArray(jsonObject.getJSONArray("tags")))
                .withId(jsonObject.getString("id"))
                .withURI(jsonObject.getString("uri"))
                .withOwner(jsonObject.getString("owner"))
                .withPolicyBox(getPolicyBox(jsonObject.getJSONObject("policy_box")))
                .build();

        return  instance;

    }

    private PolicyBox getPolicyBox(JSONObject policyJson){
        final AbstractBox abstractBox = new GenericBoxFactory().create(policyJson);
        if(abstractBox !=null && abstractBox.getType() == BoxType.POLICY){
            return (PolicyBox) abstractBox;
        }
        return null;
    }

    private AbstractBox [] getBoxes(JSONArray arrayBoxes) throws ElasticBoxModelException {
        if(!arrayBoxes.isEmpty()){
            int counter = 0;
            AbstractBox [] boxes = new AbstractBox[arrayBoxes.size()];
            for (Object boxObject : arrayBoxes) {
                JSONObject boxJson = (JSONObject) boxObject;
                final AbstractBox box = new GenericBoxFactory().create(boxJson);
                boxes[counter] = box;
                counter++;
            }
            return boxes;
        }
        return new AbstractBox[0];
    }

    private Instance.Operation getOperation(JSONObject operationObject) throws ElasticBoxModelException {
        if (!operationObject.isNullObject()){
            final String event = operationObject.getString("event");
            final String workspace = operationObject.getString("workspace");
            final String created = operationObject.getString("created");

            final Instance.OperationType operationType = Instance.OperationType.findByValue(event);
            final Instance.Operation operation = new Instance.Operation(operationType, created, workspace);
            return operation;
        }
        return null;
    }

    private Instance.Service getService(JSONObject serviceObject){
        if (!serviceObject.isNullObject()){
            final String id = serviceObject.getString("id");
            final Instance.Service service = new Instance.Service(id);
            return service;
        }
        return null;
    }

}
