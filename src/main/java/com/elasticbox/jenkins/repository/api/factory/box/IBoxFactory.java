package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.repository.api.factory.Factory;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public interface IBoxFactory<T> extends Factory<T>{

    public boolean canCreate(JSONObject jsonObject);

}
