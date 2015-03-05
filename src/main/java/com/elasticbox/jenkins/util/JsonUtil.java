/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.util;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
public class JsonUtil {

    public static JSONObject find(JSONObject source, String arrayName, String key, Object value) {
        for (Object object : source.getJSONArray(arrayName)) {
            JSONObject json = (JSONObject) object;
            if (json.containsKey(key) && ((value == null && json.getString(key) == null) || json.getString(key).equals(value))) {
                return json;
            }
        }
        return null;
    }
    
    public static JSONArray createCloudFormationDeployVariables(String providerId, String location) {
        JSONArray policyVariables = new JSONArray();
        JSONObject providerVariable = new JSONObject();
        providerVariable.put("type", "Text");
        providerVariable.put("name", "provider_id");
        providerVariable.put("value", providerId);
        policyVariables.add(providerVariable);
        JSONObject locationVariable = new JSONObject();
        locationVariable.put("type", "Text");
        locationVariable.put("name", "location");
        locationVariable.put("value", location);  
        return policyVariables;
    }
    
}
