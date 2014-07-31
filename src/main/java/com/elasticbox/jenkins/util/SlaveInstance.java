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

import com.elasticbox.Client;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxSlave;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveInstance {
    public static final String JNLP_SLAVE_OPTIONS_VARIABLE = "JNLP_SLAVE_OPTIONS";
    public static final String JENKINS_URL_VARIABLE = "JENKINS_URL";
    public static final Set<String> REQUIRED_VARIABLES = new HashSet<String>(Arrays.asList(new String[]{JENKINS_URL_VARIABLE, JNLP_SLAVE_OPTIONS_VARIABLE}));

    public static JSONArray createJenkinsVariables(String jenkinsUrl, String slaveName) {
        JSONArray variables = new JSONArray();
        JSONObject variable = new JSONObject();
        variable.put("name", SlaveInstance.JENKINS_URL_VARIABLE);
        variable.put("type", "Text");
        variable.put("value", jenkinsUrl);
        variables.add(variable);
        variable = new JSONObject();
        variable.put("name", "SLAVE_NAME");
        variable.put("type", "Text");
        variable.put("value", slaveName);
        variables.add(variable);
        return variables;
    }

    public static JSONArray createJenkinsVariables(Client client, String jenkinsUrl, ElasticBoxSlave slave) throws IOException {
        Map<String, JSONObject> requiredVariables = Collections.EMPTY_MAP;
        JSONArray boxStack = DescriptorHelper.getBoxStack(client, slave.getBoxVersion()).getJsonArray();
        for (int i = 0; i < boxStack.size(); i++) {
            requiredVariables = getRequiredVariables(boxStack.getJSONObject(i));
            if (requiredVariables.size() == REQUIRED_VARIABLES.size()) {
                break;
            }
        }
        
        if (requiredVariables.size() < REQUIRED_VARIABLES.size()) {
            throw new IOException(MessageFormat.format("No box in the runtime stack of the box version {0} has the required variables {1}.", 
                    slave.getBoxVersion(), StringUtils.join(REQUIRED_VARIABLES, ", ")));
        }
        
        JSONObject jenkinsUrlVariable = requiredVariables.get(JENKINS_URL_VARIABLE);
        String scope = jenkinsUrlVariable.containsKey("scope") ? jenkinsUrlVariable.getString("scope") : null;
        JSONArray variables = new JSONArray();
        JSONObject variable = new JSONObject();
        variable.put("name", JENKINS_URL_VARIABLE);
        variable.put("type", "Text");
        variable.put("value", jenkinsUrl);
        if (scope != null) {
            variable.put("scope", scope);
        }
        variables.add(variable);

        String options = MessageFormat.format("-jnlpUrl {0}/computer/{1}/slave-agent.jnlp -secret {2}", jenkinsUrl, slave.getNodeName(), slave.getComputer().getJnlpMac());
        variable = new JSONObject();
        variable.put("name", SlaveInstance.JNLP_SLAVE_OPTIONS_VARIABLE);
        variable.put("type", "Text");
        variable.put("value", options);
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
    
}
