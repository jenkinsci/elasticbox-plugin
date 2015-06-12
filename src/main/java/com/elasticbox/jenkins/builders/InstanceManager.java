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

package com.elasticbox.jenkins.builders;

import hudson.model.AbstractBuild;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
class InstanceManager {
    private final Map<String, JSONObject> buildIdToInstanceMap;

    public InstanceManager() {
        buildIdToInstanceMap = new ConcurrentHashMap<String, JSONObject>();
    }

    public JSONObject getInstance(AbstractBuild build) {
        return buildIdToInstanceMap.get(build.getId());
    }

    public void setInstance(AbstractBuild build, JSONObject instance) {
        buildIdToInstanceMap.put(build.getId(), instance);

        for (Iterator<String> iter = buildIdToInstanceMap.keySet().iterator(); iter.hasNext();) {
            Object _build = build.getProject().getBuild(iter.next());
            if (!(_build instanceof AbstractBuild) || !((AbstractBuild) _build).isBuilding()) {
                iter.remove();
            }
        }
    }

}
