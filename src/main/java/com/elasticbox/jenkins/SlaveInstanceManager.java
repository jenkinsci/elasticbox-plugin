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

import com.elasticbox.Client;

import hudson.model.Node;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SlaveInstanceManager {
    private final Map<String, ElasticBoxSlave> instanceIdToSlaveMap;
    private Map<ElasticBoxSlave, JSONObject> slaveToInstanceMap;
    private final Map<ElasticBoxCloud, List<JSONObject>> cloudToInstancesMap;
    private Collection<ElasticBoxSlave> slavesWithoutInstance;
    private final Map<ElasticBoxCloud, Set<String>> cloudToWorkspaceIDsMap;

    public SlaveInstanceManager() throws IOException {
        instanceIdToSlaveMap = new HashMap<String, ElasticBoxSlave>();
        cloudToInstancesMap = new HashMap<ElasticBoxCloud, List<JSONObject>>();
        cloudToWorkspaceIDsMap = new HashMap<ElasticBoxCloud, Set<String>>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                final ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getInstanceUrl() != null) {
                    String instanceId = slave.getInstanceId();
                    instanceIdToSlaveMap.put(instanceId, slave);
                    ElasticBoxCloud cloud = slave.getCloud();
                    if (cloud != null) {
                        Set<String> workspaceIDs = cloudToWorkspaceIDsMap.get(cloud);
                        if (workspaceIDs == null) {
                            workspaceIDs = new HashSet<String>();
                            cloudToWorkspaceIDsMap.put(cloud, workspaceIDs);
                        }
                        workspaceIDs.add(slave.getSlaveConfiguration().getWorkspace());
                    }
                }
            }
        }

        if (instanceIdToSlaveMap.isEmpty()) {
            slavesWithoutInstance = Collections.emptyList();
        }
    }

    public ElasticBoxSlave getSlave(String instanceId) {
        return instanceIdToSlaveMap.get(instanceId);
    }

    public Collection<ElasticBoxSlave> getSlaves() {
        return instanceIdToSlaveMap.values();
    }

    public Collection<ElasticBoxSlave> getSlavesWithoutInstance() throws IOException {
        if (slavesWithoutInstance == null) {
            ensureAllFetched();
            Set<String> validInstanceIDs = new HashSet<String>();
            for (List<JSONObject> instances : cloudToInstancesMap.values()) {
                for (JSONObject instance : instances) {
                    validInstanceIDs.add(instance.getString("id"));
                }
            }
            Map<String, ElasticBoxSlave> invalidInstanceIdToSlaveMap
                = new HashMap<String, ElasticBoxSlave>(instanceIdToSlaveMap);

            invalidInstanceIdToSlaveMap.keySet().removeAll(validInstanceIDs);
            slavesWithoutInstance = new ArrayList<ElasticBoxSlave>(invalidInstanceIdToSlaveMap.values());
        }
        return slavesWithoutInstance;
    }

    public List<JSONObject> getInstances(ElasticBoxCloud cloud) throws IOException {
        if (cloudToWorkspaceIDsMap.containsKey(cloud)) {
            List<JSONObject> instances = cloudToInstancesMap.get(cloud);
            if (instances == null) {
                // the instances of the cloud are not fetched yet
                instances = new ArrayList<JSONObject>();
                Client client = cloud.getClient();
                for (String workspaceId : cloudToWorkspaceIDsMap.get(cloud)) {
                    for (Object instance : client.getInstances(workspaceId)) {
                        JSONObject instanceJson = (JSONObject) instance;
                        String instanceId = instanceJson.getString("id");
                        if (instanceIdToSlaveMap.containsKey(instanceId)) {
                            instances.add(instanceJson);
                        }
                    }
                }
                cloudToInstancesMap.put(cloud, instances);
            }
            return instances;
        } else {
            return Collections.emptyList();
        }
    }

    public Collection<JSONObject> getInstances() throws IOException {
        return getSlaveToInstanceMap().values();
    }

    public JSONObject getInstance(ElasticBoxSlave slave) throws IOException {
        return getSlaveToInstanceMap().get(slave);
    }

    private Map<ElasticBoxSlave, JSONObject> getSlaveToInstanceMap() throws IOException {
        if (slaveToInstanceMap == null) {
            ensureAllFetched();
            slaveToInstanceMap = new HashMap<ElasticBoxSlave, JSONObject>();
            for (List<JSONObject> cloudInstances : cloudToInstancesMap.values()) {
                for (JSONObject instance : cloudInstances) {
                    slaveToInstanceMap.put(getSlave(instance.getString("id")), instance);
                }
            }
        }
        return slaveToInstanceMap;
    }

    private void ensureAllFetched() throws IOException {
        Set<ElasticBoxCloud> unfetchedClouds = new HashSet(cloudToWorkspaceIDsMap.keySet());
        unfetchedClouds.removeAll(cloudToInstancesMap.keySet());
        for (ElasticBoxCloud cloud : unfetchedClouds) {
            getInstances(cloud);
        }
    }
}
