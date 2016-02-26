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

import com.elasticbox.BoxStack;
import com.elasticbox.Client;
import com.elasticbox.jenkins.AbstractSlaveConfiguration;
import com.elasticbox.jenkins.ElasticBoxSlave;

import hudson.model.Node;

import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SlaveInstance {
    public static final String JNLP_SLAVE_OPTIONS_VARIABLE = "JNLP_SLAVE_OPTIONS";
    public static final String JENKINS_URL_VARIABLE = "JENKINS_URL";
    public static final Set<String> REQUIRED_VARIABLES
        = new HashSet<String>(Arrays.asList(new String[]{JENKINS_URL_VARIABLE, JNLP_SLAVE_OPTIONS_VARIABLE}));

    public static String createJnlpSlaveOptions(ElasticBoxSlave slave) {
        return MessageFormat.format("-jnlpUrl {0}/computer/{1}/slave-agent.jnlp -secret {2}",
                Jenkins.getInstance().getRootUrl(), slave.getNodeName(), slave.getComputer().getJnlpMac());
    }

    public static JSONArray createJenkinsVariables(String jenkinsUrl, String slaveName) {

        JSONObject variable = new JSONObject();
        variable.put("name", SlaveInstance.JENKINS_URL_VARIABLE);
        variable.put("type", "Text");
        variable.put("value", jenkinsUrl);

        JSONArray variables = new JSONArray();
        variables.add(variable);

        variable = new JSONObject();
        variable.put("name", "SLAVE_NAME");
        variable.put("type", "Text");
        variable.put("value", slaveName);

        variables.add(variable);

        return variables;
    }

    public static JSONArray createJenkinsVariables(Client client, ElasticBoxSlave slave) throws IOException {
        Map<String, JSONObject> requiredVariables = Collections.EMPTY_MAP;

        JSONArray boxStack = new BoxStack(
                            slave.getBoxVersion(),
                            client.getBoxStack(slave.getBoxVersion()),
                            client
        ).toJsonArray();

        for (int i = 0; i < boxStack.size(); i++) {
            requiredVariables = getRequiredVariables(boxStack.getJSONObject(i));
            if (requiredVariables.size() == REQUIRED_VARIABLES.size()) {
                break;
            }
        }

        if (requiredVariables.size() < REQUIRED_VARIABLES.size()) {
            throw new IOException(
                MessageFormat.format(
                    "No box in the runtime stack of the box version {0} has the required variables {1}.",
                    slave.getBoxVersion(),
                    StringUtils.join(REQUIRED_VARIABLES, ", ")));
        }

        JSONObject jenkinsUrlVariable = requiredVariables.get(JENKINS_URL_VARIABLE);
        String jenkinsUrl = Jenkins.getInstance().getRootUrl();


        JSONObject variable = new JSONObject();
        variable.put("name", JENKINS_URL_VARIABLE);
        variable.put("type", "Text");
        variable.put("value", jenkinsUrl);

        String scope = jenkinsUrlVariable.containsKey("scope") ? jenkinsUrlVariable.getString("scope") : null;
        if (scope != null) {
            variable.put("scope", scope);
        }

        JSONArray variables = new JSONArray();
        variables.add(variable);

        variable = new JSONObject();
        variable.put("name", SlaveInstance.JNLP_SLAVE_OPTIONS_VARIABLE);
        variable.put("type", "Text");
        variable.put("value", createJnlpSlaveOptions(slave));
        if (scope != null) {
            variable.put("scope", scope);
        }
        variables.add(variable);
        return variables;
    }

    private static Map<String, JSONObject> getRequiredVariables(JSONObject boxJson) {
        Map<String, JSONObject> requiredVariables = new HashMap<String, JSONObject>();
        JSONArray variables = boxJson.getJSONArray("variables");
        for (int i = 0; i < variables.size(); i++) {
            JSONObject variable = variables.getJSONObject(i);
            String name = variable.getString("name");
            if (variable.getString("type").equals("Text") && REQUIRED_VARIABLES.contains(name)) {
                requiredVariables.put(name, variable);
            }
        }

        return requiredVariables;
    }

    public static boolean isSlaveBox(JSONObject boxJson) {
        return getRequiredVariables(boxJson).size() == REQUIRED_VARIABLES.size();
    }

    public static Map<String, Integer> getSlaveConfigIdToInstanceCountMap(List<JSONObject> activeInstances) {
        Map<String, String> slaveNameToConfigIdMap = new HashMap<String, String>();
        for (Node node : Jenkins.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                AbstractSlaveConfiguration config = slave.getSlaveConfiguration();
                if (config != null) {
                    slaveNameToConfigIdMap.put(slave.getNodeName(), config.getId());
                }
            }
        }
        Map<String, Integer> slaveConfigIdToInstanceCountMap = new HashMap<String, Integer>();
        for (JSONObject instance : activeInstances) {
            List<?> tags = new ArrayList(instance.getJSONArray("tags"));
            tags.retainAll(slaveNameToConfigIdMap.keySet());
            if (!tags.isEmpty()) {
                String slaveName = (String) tags.get(0);
                String slaveConfigId = slaveNameToConfigIdMap.get(slaveName);
                Integer instanceCount = slaveConfigIdToInstanceCountMap.get(slaveConfigId);
                slaveConfigIdToInstanceCountMap.put(slaveConfigId, instanceCount == null ? 1 : ++instanceCount);
            }
        }

        return slaveConfigIdToInstanceCountMap;
    }

    public static class InstanceCounter {
        private final Map<String, Integer> slaveConfigIdToInstanceCountMap;

        public InstanceCounter(List<JSONObject> activeInstances) {
            Map<String, AbstractSlaveConfiguration> instanceIdToSlaveConfigMap
                = new HashMap<String, AbstractSlaveConfiguration>();

            for (Node node : Jenkins.getInstance().getNodes()) {
                if (node instanceof ElasticBoxSlave) {
                    ElasticBoxSlave slave = (ElasticBoxSlave) node;
                    AbstractSlaveConfiguration config = slave.getSlaveConfiguration();
                    if (config != null) {
                        instanceIdToSlaveConfigMap.put(slave.getInstanceId(), slave.getSlaveConfiguration());
                    }
                }
            }
            slaveConfigIdToInstanceCountMap = new HashMap<String, Integer>();
            for (JSONObject instance : activeInstances) {
                AbstractSlaveConfiguration slaveConfig = instanceIdToSlaveConfigMap.get(instance.getString("id"));
                if (slaveConfig != null) {
                    Integer instanceCount = slaveConfigIdToInstanceCountMap.get(slaveConfig.getId());
                    slaveConfigIdToInstanceCountMap.put(
                        slaveConfig.getId(),
                        instanceCount == null
                            ? 1
                            : ++instanceCount);
                }
            }
        }

        /**
         * Counts the active instances created with the specified slave configurations.
         *
         * @param slaveConfig the slave configuration
         * @return the number of instances created with the specified slave configuration
         */
        public int count(AbstractSlaveConfiguration slaveConfig) {
            Integer instanceCount = slaveConfigIdToInstanceCountMap.get(slaveConfig.getId());
            return instanceCount == null ? 0 : instanceCount;
        }

    }

}
