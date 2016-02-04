package com.elasticbox.jenkins.model.repository.api.criteria.box;

import com.elasticbox.jenkins.model.box.BoxType;
import net.sf.json.JSONObject;

/**
 * Created by serna on 12/3/15.
 */
public class NoPolicyAndNoApplicationBoxes extends NoPolicyBoxesJSONCriteria {

    @Override
    boolean performFit(JSONObject jsonObject) {

        final boolean isPolicy = super.performFit(jsonObject);

        String schema = jsonObject.getString("schema");

        if(BoxType.APPLICATION.isType(schema)){
            return false;
        }

        if (isPolicy)
            return true;

        return false;

    }
}
