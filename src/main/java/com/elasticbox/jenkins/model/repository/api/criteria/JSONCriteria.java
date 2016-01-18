package com.elasticbox.jenkins.model.repository.api.criteria;

import net.sf.json.JSONArray;

import java.util.List;

/**
 * Created by serna on 11/27/15.
 */
public interface JSONCriteria<T> {

    List<T> filter(JSONArray array);

}
