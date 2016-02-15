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

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class BoxFactory extends AbstractBoxTransformer<AbstractBox> {

    public static AbstractBoxTransformer[] boxCreationActions = new AbstractBoxTransformer[]{
        new ScriptBoxTransformer(),
            new PolicyBoxTransformer(),
                new TemplateCloudFormationBoxTransformer(),
                    new ManagedCloudFormationBoxTransformer(),
                        new ApplicationBoxTransformer(),
                            new ContainerBoxTransformer()

    };

    @Override
    public boolean shouldApply(JSONObject jsonObject) {
        final String schema = jsonObject.getString("schema");
        return BoxType.isBox(schema);
    }

    @Override
    public AbstractBox apply(JSONObject jsonObject) {
        final String schema = jsonObject.getString("schema");

        if(StringUtils.isNotBlank(schema) && BoxType.isBox(schema)){

            final BoxType boxType = BoxType.getType(schema);

            for (AbstractBoxTransformer<AbstractBox> action : boxCreationActions) {
                if (action.shouldApply(jsonObject)){
                    return action.apply(jsonObject);
                }
            }

        }
        throw new ElasticBoxModelException("There is no factory for building: "+schema);    }
}