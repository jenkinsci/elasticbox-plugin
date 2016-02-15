/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.repository.api.deserializer.filter.boxes;

import com.elasticbox.jenkins.model.repository.api.deserializer.filter.Filter;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 2/12/16.
 */
public class CompositeBoxFilter implements Filter<JSONObject> {

    private List<Filter<JSONObject>> filters =  new ArrayList<>();

    public CompositeBoxFilter(List<Filter<JSONObject>> filters) {
        this.filters = filters;
    }

    public CompositeBoxFilter() {
        this.filters = filters;
    }

    public CompositeBoxFilter add(Filter<JSONObject> filter){
        filters.add(filter);
        return this;
    }

    @Override
    public boolean apply(JSONObject it) {
        for(Filter<JSONObject> filter : getFilters()){
            if (!filter.apply(it)){
                return false;
            }
        }
        return true;
    }

    public List<Filter<JSONObject>> getFilters() {
        return filters;
    }
}
