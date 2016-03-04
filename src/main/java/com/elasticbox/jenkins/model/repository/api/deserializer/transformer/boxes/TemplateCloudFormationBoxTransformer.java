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
import com.elasticbox.jenkins.model.box.cloudformation.TemplateCloudFormationBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.api.deserializer.Utils;
import net.sf.json.JSONObject;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TemplateCloudFormationBoxTransformer extends AbstractBoxTransformer<TemplateCloudFormationBox> {

    private static final Logger logger = Logger.getLogger(TemplateCloudFormationBoxTransformer.class.getName());

    @Override
    public TemplateCloudFormationBox apply(JSONObject jsonObject) throws ElasticBoxModelException {

        TemplateCloudFormationBox templateCloudFormationBox
            = new TemplateCloudFormationBox.TemplateCloudFormationBoxBuilder()

                .withOwner(jsonObject.getString("owner"))
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .withRequirements(Utils.toStringArray(jsonObject.getJSONArray("requirements")))
                .build();

        return templateCloudFormationBox;
    }

    @Override
    public boolean shouldApply(JSONObject jsonObject) {

        if (super.canCreate(jsonObject, BoxType.CLOUDFORMATION)) {
            final String type = jsonObject.getString("type");
            try {
                final CloudFormationBoxType cloudFormationBoxType = CloudFormationBoxType.getType(type);
                return cloudFormationBoxType == CloudFormationBoxType.TEMPLATE;
            } catch (ElasticBoxModelException e) {
                logger.log(Level.SEVERE, "There is no CloudFormation type for type: " + type);
                e.printStackTrace();
            }

        }

        return false;
    }
}
