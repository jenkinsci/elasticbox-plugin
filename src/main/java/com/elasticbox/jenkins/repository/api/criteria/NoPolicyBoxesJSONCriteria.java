package com.elasticbox.jenkins.repository.api.criteria;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.repository.api.factory.ModelFactory;
import com.elasticbox.jenkins.repository.api.factory.box.BoxFactory;
import com.elasticbox.jenkins.repository.api.factory.box.GenericBoxFactory;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/26/15.
 */
public class NoPolicyBoxesJSONCriteria extends BoxJSONCriteria<AbstractBox> {

    public NoPolicyBoxesJSONCriteria() {
        super(new GenericBoxFactory());
    }

    public NoPolicyBoxesJSONCriteria(BoxFactory<AbstractBox> factory) {
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