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

package com.elasticbox.jenkins;

import hudson.model.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveInstanceManager {
    private final Map<String, ElasticBoxSlave> instanceIdToSlaveMap;
    private final Map<ElasticBoxCloud, List<JSONObject>> cloudToInstancesMap;
    private final Collection<ElasticBoxSlave> slavesWithoutInstance;
    
    public SlaveInstanceManager() throws IOException {
        instanceIdToSlaveMap = new HashMap<String, ElasticBoxSlave>();
        cloudToInstancesMap = new HashMap<ElasticBoxCloud, List<JSONObject>>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                final ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getInstanceUrl() != null) {
                    instanceIdToSlaveMap.put(slave.getInstanceId(), slave);
                }
            }
        }    
        
        if (!instanceIdToSlaveMap.isEmpty()) {
            Map<ElasticBoxCloud, List<String>> cloudToInstanceIDsMap = new HashMap<ElasticBoxCloud, List<String>>();
            for (Map.Entry<String, ElasticBoxSlave> entry : instanceIdToSlaveMap.entrySet()) {
                ElasticBoxSlave slave = entry.getValue();
                ElasticBoxCloud cloud = slave.getCloud();
                List<String> instanceIDs = cloudToInstanceIDsMap.get(cloud);
                if (instanceIDs == null) {
                    instanceIDs = new ArrayList<String>();
                    cloudToInstanceIDsMap.put(cloud, instanceIDs);
                }
                instanceIDs.add(slave.getInstanceId());
            }
            
            Set<String> validInstanceIDs = new HashSet<String>();
            for (Map.Entry<ElasticBoxCloud, List<String>> entry : cloudToInstanceIDsMap.entrySet()) {
                ElasticBoxCloud cloud = entry.getKey();
                List<JSONObject> instances = new ArrayList<JSONObject>();
                for (Object instance : cloud.createClient().getInstances(entry.getValue())) {
                    JSONObject instanceJson = (JSONObject) instance;
                    validInstanceIDs.add(instanceJson.getString("id"));
                    instances.add(instanceJson);
                }
                cloudToInstancesMap.put(cloud, instances);
            }  
            
            
            Map<String, ElasticBoxSlave> invalidInstanceIdToSlaveMap = new HashMap<String, ElasticBoxSlave>(instanceIdToSlaveMap);
            invalidInstanceIdToSlaveMap.keySet().removeAll(validInstanceIDs);
            slavesWithoutInstance = new ArrayList<ElasticBoxSlave>(invalidInstanceIdToSlaveMap.values());
        } else {
            slavesWithoutInstance = Collections.emptyList();
        }
    }
    
    public ElasticBoxSlave getSlave(String instanceId) {
        return instanceIdToSlaveMap.get(instanceId);
    }
    
    public Collection<ElasticBoxSlave> getSlaves() {
        return instanceIdToSlaveMap.values();
    }

    public Collection<ElasticBoxSlave> getSlavesWithoutInstance() {
        return slavesWithoutInstance;
    }
    
    public List<JSONObject> getInstances(ElasticBoxCloud cloud) {
        List<JSONObject> instances = cloudToInstancesMap.get(cloud);
        if (instances != null) {
            return instances;
        } else {
            return Collections.emptyList();
        }
    }
    
    public List<JSONObject> getInstances() {
        List<JSONObject> instances = new ArrayList<JSONObject>();
        for (List<JSONObject> cloudInstances : cloudToInstancesMap.values()) {
            instances.addAll(cloudInstances);
        }
        return instances;
    }
}
