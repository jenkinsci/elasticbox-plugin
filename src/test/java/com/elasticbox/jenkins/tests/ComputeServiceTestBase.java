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
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class ComputeServiceTestBase extends TestBase {
    protected final TestUtils.MappingTemplateResolver templateResolver = new TestUtils.MappingTemplateResolver();
    protected JSONObject testProvider;
    protected JSONObject testProfile;
    protected JSONObject vSphereProfile;

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
        templateResolver.map("{linux-compute-id}", linuxBoxId);
        templateResolver.map("{linux-compute-version}", linuxBoxVersion.getString("id"));
        templateResolver.map("{linux-compute-test-profile}", testProfile.getString("id"));
        if (vSphereProfile != null) {
            templateResolver.map("{linux-compute-vsphere-profile}", vSphereProfile.getString("id"));
        }

        templateResolver.map("{workspace}", TestUtils.TEST_WORKSPACE);
    }

    protected void runTestJob(String jobTemplatePath) throws Exception {
        final String testTag = UUID.randomUUID().toString().substring(0, 30);
        try {
            Map<String, String> testParameters = Collections.singletonMap("TEST_TAG", testTag);
            String jobXml = templateResolver.resolve(TestUtils.getResourceAsString(jobTemplatePath));
            FreeStyleBuild build = TestUtils.runJob("test", jobXml, testParameters, jenkins.getInstance());
            TestUtils.assertBuildSuccess(build);
            validate(build);
        } finally {
            TestUtils.cleanUp(testTag, Jenkins.getInstance());
        }
    }

    protected abstract void validate(AbstractBuild build) throws Exception;
}
