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

package com.elasticbox.jenkins.tests;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.AbstractSlaveConfiguration;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.ElasticBoxSlave;
import com.elasticbox.jenkins.SlaveConfiguration;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.util.Condition;
import com.elasticbox.jenkins.util.SlaveInstance;
import hudson.model.Node;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import jenkins.model.JenkinsLocationConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.Mockito;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveProvisionTestBase extends BuildStepTestBase {


    @Before
    @Override
    public void setup() throws Exception {
        String jenkinsUrl = jenkinsRule.getInstance().getRootUrl();
        if (StringUtils.isBlank(jenkinsUrl)) {
            jenkinsUrl = jenkinsRule.createWebClient().getContextPath();
        }
        if (StringUtils.isNotBlank(TestUtils.JENKINS_PUBLIC_HOST)) {
            jenkinsUrl = jenkinsUrl.replace("localhost", TestUtils.JENKINS_PUBLIC_HOST);
        }
        JenkinsLocationConfiguration.get().setUrl(jenkinsUrl);

        super.setup();
    }

    protected SlaveConfiguration createSlaveConfiguration(String slaveBoxName, JSONArray variables) throws IOException {
        TestBoxData testBoxData = testBoxDataLookup.get(slaveBoxName);
        return new SlaveConfiguration(UUID.randomUUID().toString(), TestUtils.TEST_WORKSPACE,
                testBoxData.getJson().getString("id"), DescriptorHelper.LATEST_BOX_VERSION,
                testBoxData.getNewProfileId(), null, null, null, 1, 2, slaveBoxName, variables.toString(),
                UUID.randomUUID().toString(), slaveBoxName + "_TestSlaveCfg", null, Node.Mode.NORMAL,
                0, null, 1, 60, DeploymentType.SCRIPTBOX_DEPLOYMENT_TYPE.getValue() );
    }

    protected void provisionSlaves() throws Exception {
        JSONArray variables = new JSONArray();
        SlaveConfiguration testLinuxBoxSlaveConfig = createSlaveConfiguration("test-linux-box", variables);
        JSONObject variable = new JSONObject();
        variable.put("name", "BINDING");
        variable.put("type", "Binding");
        variable.put("scope", "");
        variable.put("tags", Arrays.asList(TestUtils.TEST_BINDING_INSTANCE_TAG));
        variable.put("visibility", Constants.PRIVATE_VISIBILITY);
        variables.add(variable);
        SlaveConfiguration testNestedBoxSlaveConfig = createSlaveConfiguration("test-nested-box", variables);
        variables.clear();
        variable.put("scope", "nested");
        variables.add(variable);
        SlaveConfiguration testDeeplyNestedBoxSlaveConfig = createSlaveConfiguration("test-deeply-nested-box", variables);
        ElasticBoxCloud elasticBoxCloudMock = new ElasticBoxCloud("elasticbox-" + UUID.randomUUID().toString(), "ElasticBox",
                TestUtils.ELASTICBOX_URL, 6, TestUtils.CLOUD_CREDENTIALS_ID,
                Arrays.asList(testLinuxBoxSlaveConfig, testNestedBoxSlaveConfig, testDeeplyNestedBoxSlaveConfig));
        ElasticBoxCloud testCloud = Mockito.spy(elasticBoxCloudMock);
        Mockito.doReturn(TestUtils.ACCESS_TOKEN).when(testCloud).getTokenFromCredentials(TestUtils.ELASTICBOX_URL, TestUtils.CLOUD_CREDENTIALS_ID);
        jenkinsRule.getInstance().clouds.add(testCloud);

        // wait for new slave to be launched
        new Condition() {

            @Override
            public boolean satisfied() {
                return jenkinsRule.getInstance().getNodes().size() > 3;
            }
        }.waitUntilSatisfied(60);

        // wait some more to check that number of launched slaves should not exceed the minimum number configured
        Thread.sleep(10000);

        List<ElasticBoxSlave> slaves = new ArrayList<ElasticBoxSlave>();
        for (Node node : jenkinsRule.getInstance().getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                slaves.add((ElasticBoxSlave) node);
            }
        }

        Assert.assertEquals(3, slaves.size());

        Map<AbstractSlaveConfiguration, String> configToSlaveScopeMap = new HashMap<AbstractSlaveConfiguration, String>();
        configToSlaveScopeMap.put(testNestedBoxSlaveConfig, "nested");
        configToSlaveScopeMap.put(testDeeplyNestedBoxSlaveConfig, "nested.nested");
        Map<AbstractSlaveConfiguration, ElasticBoxSlave> configToSlaveMap = new HashMap<AbstractSlaveConfiguration, ElasticBoxSlave>();
        configToSlaveMap.put(testLinuxBoxSlaveConfig, null);
        configToSlaveMap.put(testNestedBoxSlaveConfig, null);
        configToSlaveMap.put(testDeeplyNestedBoxSlaveConfig, null);
        for (ElasticBoxSlave slave : slaves) {
            AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
            Assert.assertTrue("Unexpected slave " + slave.getDisplayName(), configToSlaveMap.containsKey(slaveConfig));
            configToSlaveMap.put(slaveConfig, slave);
            validateSlave(slave, configToSlaveScopeMap.get(slaveConfig));
            Assert.assertEquals("Slave and instance name doesn't match:", slave.getDisplayName(), slave.getInstance().getString("name") );
        }

        for (Map.Entry<AbstractSlaveConfiguration, ElasticBoxSlave> entry : configToSlaveMap.entrySet()) {
            Assert.assertNotNull(MessageFormat.format("Slave was not launched for box {0}", entry.getKey().getBox()), entry.getValue());
        }
    }

    private void validateSlave(final ElasticBoxSlave slave, String slaveScope) throws Exception {
        new Condition() {

            @Override
            public boolean satisfied() {
                return slave.getInstanceUrl() != null;
            }
        }.waitUntilSatisfied(30);

        JSONObject instance = slave.getCloud().getClient().getInstance(slave.getInstanceId());
        JSONArray variables = instance.getJSONArray("variables");
        deleteAfter(instance);
        JSONObject jenkinsUrlVariable = null;
        JSONObject jnlpSlaveOptionsVariable = null;
        for (Object variable : variables) {
            JSONObject variableJson = (JSONObject) variable;
            if (variableJson.getString("name").equals(SlaveInstance.JENKINS_URL_VARIABLE)) {
                jenkinsUrlVariable = variableJson;
            } else if (variableJson.getString("name").equals(SlaveInstance.JNLP_SLAVE_OPTIONS_VARIABLE)) {
                jnlpSlaveOptionsVariable = variableJson;
            }
            if (jenkinsUrlVariable != null && jnlpSlaveOptionsVariable != null) {
                break;
            }
        }
        Assert.assertNotNull(jenkinsUrlVariable);
        Assert.assertEquals(jenkinsRule.getInstance().getRootUrl(), jenkinsUrlVariable.getString("value"));
        Assert.assertNotNull(jnlpSlaveOptionsVariable);
        Assert.assertEquals(SlaveInstance.createJnlpSlaveOptions(slave), jnlpSlaveOptionsVariable.getString("value"));
        if (StringUtils.isBlank(slaveScope)) {
            Assert.assertFalse(jenkinsUrlVariable.has("scope"));
            Assert.assertFalse(jnlpSlaveOptionsVariable.has("scope"));
        } else {
            Assert.assertEquals(slaveScope, jenkinsUrlVariable.getString("scope"));
            Assert.assertEquals(slaveScope, jnlpSlaveOptionsVariable.getString("scope"));
        }
    }
}
