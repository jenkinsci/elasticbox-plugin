package com.elasticbox.jenkins.repository.api.factory;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public interface Factory<T> {

    T create(JSONObject jsonObject) throws ElasticBoxModelException;;

}
