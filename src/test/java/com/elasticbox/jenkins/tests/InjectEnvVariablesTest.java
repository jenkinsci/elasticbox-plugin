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

package com.elasticbox.jenkins.tests;

import com.elasticbox.Client;
import hudson.model.FreeStyleBuild;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class InjectEnvVariablesTest extends TestBase {
    private final TestUtils.MappingTemplateResolver templateResolver = new TestUtils.MappingTemplateResolver();
    private JSONObject testProvider;
    private JSONObject testProfile;
    private JSONObject vSphereProfile;

    @Before
    public void setupTestData() throws Exception {
        Client client = cloud.getClient();
        // find Linux Compute box
        JSONObject linuxBox = null;
        for (Object box : client.getBoxes(TestUtils.TEST_WORKSPACE)) {
            JSONObject boxJson = (JSONObject) box;
            if (boxJson.getString("name").equals(TestUtils.LINUX_COMPUTE) && boxJson.getString("visibility").equals("public")) {
                linuxBox = boxJson;
                break;
            }
        }
        Assert.assertNotNull(MessageFormat.format("Cannot find public box Linux Compute in workspace ''{0}''", TestUtils.TEST_WORKSPACE), linuxBox);
        String linuxBoxId = linuxBox.getString("id");
        JSONObject linuxBoxVersion = client.getBoxVersions(linuxBoxId).getJSONObject(0);
        for (Object profile : client.getProfiles(TestUtils.TEST_WORKSPACE, linuxBoxId)) {
            JSONObject profileJson = (JSONObject) profile;
            if (profileJson.getString("name").equals("vsphere")) {
                vSphereProfile = profileJson;
                break;
            }
        }
        testProvider = TestUtils.createTestProvider(client);
        deleteAfter(testProvider);
        testProfile = TestUtils.createTestProfile(linuxBoxVersion, testProvider, null, client);
        deleteAfter(testProfile);
        templateResolver.map("tphongio", TestUtils.TEST_WORKSPACE);
        templateResolver.map("989c760d-4f3e-40bc-8c3b-6d198183b85a", linuxBoxId);
        templateResolver.map("f035c580-70b3-49ce-9209-eb90c968060a", linuxBoxVersion.getString("id"));
        templateResolver.map("ca4cf377-7b5b-4456-9c19-2131eee22747", testProfile.getString("id"));
        if (vSphereProfile != null) {
            templateResolver.map("3b348613-5522-4876-b7ea-c0e61388a87a", vSphereProfile.getString("id"));
        }
    }

    @Test
    public void testInstanceEnvVariables() throws Exception {
        runTestJob("jobs/test-instance-env-vars.xml");
    }

    public void testEnvVariablesForManyInstances() throws Exception {
        Assert.assertNotNull(MessageFormat.format("vsphere profile cannot be found for {0} in workspace ''{1}''",
                TestUtils.LINUX_COMPUTE, TestUtils.TEST_WORKSPACE), vSphereProfile);
        runTestJob("jobs/test-instances-env-vars.xml");
    }

    private void runTestJob(String jobTemplatePath) throws Exception {
        final String testTag = UUID.randomUUID().toString().substring(0, 30);
        try {
            Map<String, String> testParameters = Collections.singletonMap("TEST_TAG", testTag);
            String jobXml = templateResolver.resolve(TestUtils.getResourceAsString(jobTemplatePath));
            FreeStyleBuild build = TestUtils.runJob("test", jobXml, testParameters, jenkins.getInstance());
            TestUtils.assertBuildSuccess(build);
        } finally {
            TestUtils.cleanUp(testTag, Jenkins.getInstance());
        }
    }
}
