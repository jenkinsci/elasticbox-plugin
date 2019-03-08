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

import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlaveInstanceManager {
    private static final Logger LOGGER = Logger.getLogger(SlaveInstanceManager.class.getName());

    private final Map<String, ElasticBoxSlave> instanceIdToSlaveMap;
    private Map<ElasticBoxSlave, JSONObject> slaveToInstanceMap;
    private final Map<ElasticBoxCloud, List<JSONObject>> cloudToInstancesMap;
    private List<ElasticBoxSlave> slavesWithoutInstance;
    private final Map<ElasticBoxCloud, Set<String>> cloudToWorkspaceIDsMap;
    private boolean allFetched = false;

    public SlaveInstanceManager() throws IOException {
        instanceIdToSlaveMap = new HashMap<String, ElasticBoxSlave>();
        cloudToInstancesMap = new HashMap<ElasticBoxCloud, List<JSONObject>>();
        cloudToWorkspaceIDsMap = new HashMap<ElasticBoxCloud, Set<String>>();
        for (Node node : Jenkins.get().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                final ElasticBoxSlave slave = (ElasticBoxSlave) node;
                ElasticBoxCloud cloud = slave.getCloud();
                if (cloud != null) {
                    Set<String> workspaceIDs = cloudToWorkspaceIDsMap.get(cloud);
                    if (workspaceIDs == null) {
                        workspaceIDs = new HashSet<String>();
                        cloudToWorkspaceIDsMap.put(cloud, workspaceIDs);
                    }
                    AbstractSlaveConfiguration config = slave.getSlaveConfiguration();
                    if (config != null) {
                        workspaceIDs.add(config.getWorkspace());
                    } else {
                        LOGGER.warning("Found slave without config - " + slave);
                    }
                }
                if (slave.getInstanceUrl() != null) {
                    String instanceId = slave.getInstanceId();
                    instanceIdToSlaveMap.put(instanceId, slave);
                } else {
                    if (slavesWithoutInstance == null) {
                        slavesWithoutInstance = new ArrayList<>();
                    }
                    slavesWithoutInstance.add(slave);
                }
            }
        }

        if (slavesWithoutInstance != null) {

            ElasticBoxCloud cloud = null;
            String wks = null;
            JSONArray instances = null;
            AbstractSlaveConfiguration config;

            Iterator<ElasticBoxSlave> iterator = slavesWithoutInstance.iterator();
            while (iterator.hasNext() ) {

                ElasticBoxSlave slave = iterator.next();
                config = slave.getSlaveConfiguration();

                if ( slave.getCloud() != null && config != null) {
                    if (!slave.getCloud().equals(cloud) && !config.getWorkspace().equals(wks)) {
                        cloud = slave.getCloud();
                        wks = slave.getSlaveConfiguration().getWorkspace();
                        instances = cloud.getClient().getInstances(wks);
                    }
                    for (Object instance : instances) {
                        JSONObject instanceJson = (JSONObject) instance;
                        JSONArray tags = instanceJson.getJSONArray("tags");

                        // If the instance corresponds to a Jenkins slave, first tag will match the slave name:
                        if (tags.size() > 0 && slave.getNodeName().equals(tags.getString(0) )) {
                            String instanceId = instanceJson.getString("id");
                            if (slave.getInstanceUrl() == null) {
                                final String url = cloud.getClient().getInstanceUrl(instanceId);
                                slave.setInstanceUrl(url);
                                LOGGER.info("Linked instance [" + url + "] with orphan slave - " + slave);
                            }
                            iterator.remove();
                            instanceIdToSlaveMap.put(instanceId, slave);
                            break;
                        }
                    }
                }
            }
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder(300);
            sb.append("EB Slaves initialized:");
            sb.append("\ninstanceIdToSlaveMap=").append(instanceIdToSlaveMap);
            sb.append("\ncloudToWorkspaceIDsMap=").append(cloudToWorkspaceIDsMap);
            sb.append("\nslavesWithoutInstance=").append(slavesWithoutInstance);
            LOGGER.finest(sb.toString());
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
            Set<String> validInstanceIDs = new HashSet<>();
            for (List<JSONObject> instances : cloudToInstancesMap.values()) {
                for (JSONObject instance : instances) {
                    validInstanceIDs.add(instance.getString("id"));
                }
            }
            Map<String, ElasticBoxSlave> invalidInstanceIdToSlaveMap = new HashMap<>(instanceIdToSlaveMap);

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
        if (this.allFetched) {
            return;
        }

        Set<ElasticBoxCloud> unfetchedClouds = new HashSet(cloudToWorkspaceIDsMap.keySet());
        unfetchedClouds.removeAll(cloudToInstancesMap.keySet());
        for (ElasticBoxCloud cloud : unfetchedClouds) {
            getInstances(cloud);
        }
        this.allFetched = true;
    }

    public Map<ElasticBoxCloud, Integer> getMaxInstancesPerCloud() throws IOException {
        Map<ElasticBoxCloud, Integer> cloudToMaxNewInstancesMap = new HashMap<>();
        for (Cloud cloud : Jenkins.get().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                ElasticBoxCloud ebCloud = (ElasticBoxCloud) cloud;
                cloudToMaxNewInstancesMap.put(ebCloud, ebCloud.getMaxInstances() - getInstances(ebCloud).size());
            }
        }
        return cloudToMaxNewInstancesMap;
    }
}
