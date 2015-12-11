package com.elasticbox.jenkins.repository.api.criteria;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.repository.api.factory.ModelFactory;
import com.elasticbox.jenkins.repository.api.factory.box.BoxFactory;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/27/15.
 */
public abstract class BoxJSONCriteria<T> extends AbstractJSONCriteria<T> {

    public BoxJSONCriteria(BoxFactory<T> factory) {
        super(factory);
    }

    abstract boolean performFit(JSONObject jsonObject);

    @Override
    boolean fits(JSONObject jsonObject) {

        if(!BoxType.isBox(jsonObject.getString("schema")))
            return false;

        return performFit(jsonObject);
    }


}
