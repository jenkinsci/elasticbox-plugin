package com.elasticbox.jenkins.repository.api.criteria;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.repository.api.factory.Factory;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/26/15.
 */
public class NoPolicyBoxesJSONCriteria extends BoxJSONCriteria<ScriptBox> {

    public NoPolicyBoxesJSONCriteria(Factory<ScriptBox> factory) {
        super(factory);
    }

    @Override
    boolean performFit(JSONObject jsonObject) {

        String schema = jsonObject.getString("schema");

        if(BoxType.POLICY.isType(schema)){
                return false;
        }

        return true;

    }


}