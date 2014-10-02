/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
public class CompositeObjectFilter implements ObjectFilter {
    private final List<ObjectFilter> filters;
    
    public CompositeObjectFilter(ObjectFilter... filters) {
        this.filters = new ArrayList<ObjectFilter>(Arrays.asList(filters));
    }

    @Override
    public boolean accept(JSONObject instance) {
        for (ObjectFilter filter : filters) {
            if (!filter.accept(instance)) {
                return false;
            }
        }
        
        return true;
    }
    
    public void add(ObjectFilter filter) {
        filters.add(filter);
    }
    
}
