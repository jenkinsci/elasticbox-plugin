package com.elasticbox.jenkins.repository.api.criteria;

import net.sf.json.JSONArray;

import java.util.List;

/**
 * Created by serna on 11/27/15.
 */
public interface JSONCriteria<T> {

    List<T> fits(JSONArray array);

}
