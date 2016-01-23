package com.elasticbox.jenkins.model.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.api.factory.JSONFactoryUtils;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public class ScriptBoxFactory extends AbstractBoxFactory<ScriptBox> {
    @Override
    public ScriptBox create(JSONObject jsonObject) throws ElasticBoxModelException {

        final ScriptBox box = new ScriptBox.ScriptBoxBuilder()
                .withOwner(jsonObject.getString("owner"))
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .withRequirements(JSONFactoryUtils.toStringArray(jsonObject.getJSONArray("requirements")))
                .withMembers(getMembers(jsonObject.getJSONArray("members")))
                .build();

        return  box;
    }

    @Override
    public boolean canCreate(JSONObject jsonObject) {
        return super.canCreate(jsonObject, BoxType.SCRIPT);
    }
}
