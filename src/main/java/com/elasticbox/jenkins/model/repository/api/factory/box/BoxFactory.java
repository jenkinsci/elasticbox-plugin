package com.elasticbox.jenkins.model.repository.api.factory.box;

import com.elasticbox.jenkins.model.repository.api.factory.ModelFactory;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public interface BoxFactory<T> extends ModelFactory<T> {

    public boolean canCreate(JSONObject jsonObject);

}
