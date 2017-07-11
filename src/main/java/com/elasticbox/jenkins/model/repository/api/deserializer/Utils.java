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

package com.elasticbox.jenkins.model.repository.api.deserializer;

import com.elasticbox.jenkins.model.repository.api.deserializer.action.Action;
import com.elasticbox.jenkins.model.repository.api.deserializer.filter.Filter;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.Transformer;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static String [] toStringArray(JSONArray jsonArray) {

        if (!jsonArray.isEmpty()) {
            return (String[]) jsonArray.toArray(new String[jsonArray.size()]);
        }

        return new String[0];
    }

    public static List<JSONObject> filter(JSONArray array, Filter<JSONObject> filter) {
        List<JSONObject> results = new ArrayList<JSONObject>();
        for (Object it : array) {
            JSONObject jsonObject = (JSONObject)it;
            if (filter.apply(jsonObject)) {
                results.add(jsonObject);
            }
        }
        return results;
    }


    public static <T> List<T> filter(List<T> src, Filter<T> filter) {
        List<T> results = new ArrayList<T>();
        for (T it : src) {
            if (filter.apply(it)) {
                results.add(it);
            }
        }
        return results;
    }

    public static <T, R> List<R> transform(List<T> src, Transformer<T, R> transformer) {
        List<R> results = new ArrayList<R>();
        for (T it : src) {
            R result = transformer.apply(it);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    public static <R> List<R> transform(JSONArray array, Transformer<JSONObject, R> transformer) {
        List<R> results = new ArrayList<R>();
        for (Object it : array) {
            JSONObject jsonObject = (JSONObject)it;
            R result = transformer.apply(jsonObject);
            results.add(result);
        }
        return results;
    }

    public static <T> void map(List<T> src, Action<T> action) {
        for (T it : src) {
            action.apply(it);
        }
    }
}
