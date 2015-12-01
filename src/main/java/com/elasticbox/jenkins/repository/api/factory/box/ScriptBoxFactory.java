package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.repository.api.factory.JSONFactoryUtils;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class ScriptBoxFactory implements IBoxFactory<ScriptBox> {
    @Override
    public ScriptBox create(JSONObject jsonObject) throws ElasticBoxModelException {

        ScriptBox box = new ScriptBox.ComplexBuilder()
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .withRequirements(JSONFactoryUtils.toStringArray(jsonObject.getJSONArray("requirements")))
                .build();

        return  box;
    }

    @Override
    public boolean canCreate(JSONObject jsonObject) {

        final String schema = jsonObject.getString("schema");
        if(StringUtils.isNotBlank(schema) && BoxType.isBox(schema)){
            try {
                final BoxType boxType  = BoxType.geType(schema);
                return boxType == BoxType.SCRIPT;
            } catch (ElasticBoxModelException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
