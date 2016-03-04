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

package com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;

import net.sf.json.JSONObject;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ManagedCloudFormationBoxTransformer extends AbstractBoxTransformer<ManagedCloudFormationBox> {

    private static final Logger logger = Logger.getLogger(ManagedCloudFormationBoxTransformer.class.getName());

    @Override
    public ManagedCloudFormationBox apply(JSONObject jsonObject) throws ElasticBoxModelException {

        ManagedCloudFormationBox managedCloudFormationBox =
            new ManagedCloudFormationBox.ManagedCloudFormationPolicyBoxBuilder()

                .withOwner(jsonObject.getString("owner"))
                .withProfileType(jsonObject.getJSONObject("profile").getString("schema"))
                .withId(jsonObject.getString("id"))
                .withMembers(getMembers(jsonObject.getJSONArray("members")))
                .withName(jsonObject.getString("name"))
                .build();

        return managedCloudFormationBox;
    }

    @Override
    public boolean shouldApply(JSONObject jsonObject) {

        if (super.canCreate(jsonObject, BoxType.CLOUDFORMATION)) {

            final String type = jsonObject.getString("type");

            try {
                final CloudFormationBoxType cloudFormationBoxType = CloudFormationBoxType.getType(type);
                return cloudFormationBoxType == CloudFormationBoxType.MANAGED;
            } catch (ElasticBoxModelException e) {
                logger.log(Level.SEVERE, "There is no CloudFormation type for type: " + type);
                e.printStackTrace();
            }

        }
        return false;
    }
}
