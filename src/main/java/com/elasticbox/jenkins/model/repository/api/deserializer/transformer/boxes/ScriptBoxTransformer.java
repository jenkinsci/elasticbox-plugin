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
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.repository.api.deserializer.Utils;
import net.sf.json.JSONObject;

public class ScriptBoxTransformer extends AbstractBoxTransformer<ScriptBox> {

    @Override
    public boolean shouldApply(JSONObject jsonObject) {
        return super.canCreate(jsonObject, BoxType.SCRIPT);
    }

    @Override
    public ScriptBox apply(JSONObject jsonObject) {
        final ScriptBox box = new ScriptBox.ScriptBoxBuilder()
                .withOwner(jsonObject.getString("owner"))
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .withRequirements(Utils.toStringArray(jsonObject.getJSONArray("requirements")))
                .withMembers(getMembers(jsonObject.getJSONArray("members")))
                .build();

        return  box;
    }
}
