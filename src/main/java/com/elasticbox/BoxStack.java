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

package com.elasticbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
public class BoxStack {
    private final List<JSONObject> overriddenVariables;
    private final JSONArray boxes;
    private final String boxId;
    private final Client client;

    public BoxStack(String boxId, JSONArray boxes, Client client) {
        this(boxId, boxes, client, new ArrayList<JSONObject>());
    }

    public BoxStack(String boxId, JSONArray boxes, Client client, List<JSONObject> overridenVariables) {
        this.boxId = boxId;
        this.boxes = boxes;
        this.client = client;
        this.overriddenVariables = overridenVariables;
    }

    public JSONArray toJSONArray() {
        return JSONArray.fromObject(createBoxStack("", boxId));
    }

    public JSONObject findBox(String boxId) {
        for (Object json : boxes) {
            JSONObject box = (JSONObject) json;
            if (box.getString("id").equals(boxId)) {
                return box;
            }
        }
        for (Object json : boxes) {
            JSONObject box = (JSONObject) json;
            if (box.containsKey("version") && box.getJSONObject("version").getString("box").equals(boxId)) {
                return box;
            }
        }
        return null;
    }

    private JSONObject findOverriddenVariable(String name, String scope) {
        for (Object json : overriddenVariables) {
            JSONObject variable = (JSONObject) json;
            if (scope.equals(variable.get("scope")) && variable.get("name").equals(name)) {
                return variable;
            }
        }
        return null;
    }

    private List<JSONObject> createBoxStack(String scope, String boxId) {
        JSONObject box = findBox(boxId);
        if (box == null) {
            return Collections.EMPTY_LIST;
        }
        List<JSONObject> boxStack = new ArrayList<JSONObject>();
        JSONObject stackBox = new JSONObject();
        String icon = null;
        if (box.containsKey("icon")) {
            icon = box.getString("icon");
        }
        if (icon == null || icon.isEmpty()) {
            icon = "/images/platform/box.png";
        } else if (icon.charAt(0) != '/') {
            icon = '/' + icon;
        }
        stackBox.put("id", box.getString("id"));
        stackBox.put("name", box.getString("name"));
        stackBox.put("icon", client.getEndpointUrl() + icon);
        boxStack.add(stackBox);
        JSONArray stackBoxVariables = new JSONArray();
        JSONArray variables = box.getJSONArray("variables");
        List<JSONObject> boxVariables = new ArrayList<JSONObject>();
        for (Object json : variables) {
            JSONObject variable = (JSONObject) json;
            String varScope = (String) variable.get("scope");
            if (varScope != null && !varScope.isEmpty()) {
                String fullScope = scope.isEmpty() ? varScope : scope + '.' + varScope;
                if (findOverriddenVariable(variable.getString("name"), scope) == null) {
                    JSONObject overriddenVariable = JSONObject.fromObject(variable);
                    overriddenVariable.put("scope", fullScope);
                    overriddenVariables.add(variable);
                }
            } else if (variable.getString("type").equals("Box")) {
                boxVariables.add(variable);
            } else {
                JSONObject stackBoxVariable = JSONObject.fromObject(variable);
                stackBoxVariable.put("scope", scope);
                JSONObject overriddenVariable = findOverriddenVariable(stackBoxVariable.getString("name"), scope);
                if (overriddenVariable != null) {
                    stackBoxVariable.put("value", overriddenVariable.get("value"));
                }
                stackBoxVariables.add(stackBoxVariable);
            }
        }
        stackBox.put("variables", stackBoxVariables);
        for (JSONObject boxVariable : boxVariables) {
            String variableName = boxVariable.getString("name");
            boxStack.addAll(createBoxStack(scope.isEmpty() ? variableName : scope + '.' + variableName, boxVariable.getString("value")));
        }
        return boxStack;
    }
    
}
