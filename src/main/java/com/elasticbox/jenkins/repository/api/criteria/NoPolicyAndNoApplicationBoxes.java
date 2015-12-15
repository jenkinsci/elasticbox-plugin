package com.elasticbox.jenkins.repository.api.criteria;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.repository.api.factory.ModelFactory;
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
