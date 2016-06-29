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
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class BuildStepTest extends BuildStepTestBase {
    protected static final Logger LOGGER = Logger.getLogger(BuildStepTest.class.getName());

    @Test
    public void testBuildWithSteps() throws Exception {
        FreeStyleProject project = (FreeStyleProject) jenkins.getInstance().createProjectFromXML("test",
                new ByteArrayInputStream(createTestDataFromTemplate("jobs/test-job.xml").getBytes()));
        LOGGER.info(MessageFormat.format("Testing build steps with project: ", project));

        // copy files for file variables
        FilePath workspace = jenkins.getInstance().getWorkspaceFor(project);
        File testNestedBoxJsonFile = new File(workspace.getRemote(), "test-nested-box.json");
        File jenkinsImageFile = new File(workspace.getRemote(), "jenkins.png");
        FileUtils.copyURLToFile(TestUtils.class.getResource("boxes/test-nested-box.json"), testNestedBoxJsonFile);
        FileUtils.copyURLToFile(TestUtils.class.getResource("jenkins.png"), jenkinsImageFile);

        final String testTag = UUID.randomUUID().toString().substring(0, 30);
        Map<String, String> testParameters = Collections.singletonMap("TEST_TAG", testTag);
        FreeStyleBuild build = TestUtils.runJob(project, testParameters, jenkins.getInstance());
        TestUtils.assertBuildSuccess(build);

        // validate the results of executed build steps
        VariableResolver variableResolver = new VariableResolver(cloud.name, TestUtils.TEST_WORKSPACE, build, TaskListener.NULL);
        String jobNameAndBuildId = MessageFormat.format("{0}-{1}", variableResolver.resolve("${JOB_NAME}"), variableResolver.resolve("${BUILD_ID}"));
        String buildTag = variableResolver.resolve("${BUILD_TAG}");

        Client client = new Client(cloud.getEndpointUrl(), cloud.getToken());

        JSONObject testLinuxBox = getTestBox(TestUtils.TEST_LINUX_BOX_NAME);
        JSONObject testBindingBox = getTestBox(TestUtils.TEST_BINDING_BOX_NAME);
        JSONObject testNestedBox = getTestBox(TestUtils.TEST_NESTED_BOX_NAME);

        assertNotNull(MessageFormat.format("Cannot find box {0}", TestUtils.TEST_LINUX_BOX_NAME), testLinuxBox);
        assertNotNull(MessageFormat.format("Cannot find box {0}", TestUtils.TEST_BINDING_BOX_NAME), testBindingBox);
        assertNotNull(MessageFormat.format("Cannot find box {0}", TestUtils.TEST_NESTED_BOX_NAME), testNestedBox);

        JSONArray instances = client.getInstances(TestUtils.TEST_WORKSPACE);
        List<String> instanceIDs = new ArrayList<String>();
        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;
            if (instanceJson.getJSONArray("tags").contains(testTag)) {
                instanceIDs.add(instanceJson.getString("id"));
            }
        }
        instances = client.getInstances(TestUtils.TEST_WORKSPACE, instanceIDs);

        JSONObject testLinuxBoxInstance = null;
        JSONObject testBindingBoxInstance1 = null;
        JSONObject testBindingBoxInstance2 = null;
        JSONObject testBindingBoxInstance3 = null;
        JSONObject testNestedBoxInstance = null;
        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;
            String mainBoxId = instanceJson.getJSONArray("boxes").getJSONObject(0).getString("id");
            JSONArray tags = instanceJson.getJSONArray("tags");
            if (mainBoxId.equals(testLinuxBox.getString("id"))) {
                assertNull(MessageFormat.format("The build deployed more than one instance of box {0}", TestUtils.TEST_LINUX_BOX_NAME), testLinuxBoxInstance);
                assertTrue(MessageFormat.format("The instance {0} does not have tag {1}", client.getPageUrl(instanceJson), testTag), tags.contains(testTag));
                testLinuxBoxInstance = instanceJson;
            } else if (mainBoxId.equals(testNestedBox.getString("id"))) {
                assertNull(MessageFormat.format("The build deployed more than one instance of box {0}", TestUtils.TEST_NESTED_BOX_NAME), testNestedBoxInstance);
                assertTrue(MessageFormat.format("The instance {0} does not have tag {1}", client.getPageUrl(instanceJson), testTag), tags.contains(testTag));
                testNestedBoxInstance = instanceJson;
            } else if (mainBoxId.equals(testBindingBox.getString("id"))) {
                if (tags.contains(jobNameAndBuildId)) {
                    assertNull(MessageFormat.format("The build deployed more than one instance of box {0} with tag ''{1}''", TestUtils.TEST_BINDING_BOX_NAME, jobNameAndBuildId), testBindingBoxInstance2);
                    testBindingBoxInstance2 = instanceJson;
                } else if (tags.contains(buildTag)) {
                    assertNull(MessageFormat.format("The build deployed more than one instance of box {0} with tag ''{1}''", TestUtils.TEST_BINDING_BOX_NAME, buildTag), testBindingBoxInstance3);
                    testBindingBoxInstance3 = instanceJson;
                } else if (tags.contains(testTag)) {
                    assertNull(MessageFormat.format("The build deployed more than one instance of box {0} with tags ''{1}''", TestUtils.TEST_BINDING_BOX_NAME, tags), testBindingBoxInstance1);
                    testBindingBoxInstance1 = instanceJson;
                } else {
                    throw new AssertionError(MessageFormat.format("Unexpected instance of box {0} with tags ''{1}'' has been deployed", TestUtils.TEST_BINDING_BOX_NAME, tags));
                }
            }
        }

        assertNotNull(testLinuxBoxInstance);
        assertNotNull(testBindingBoxInstance1);
        assertNotNull(testBindingBoxInstance2);
        assertNotNull(testBindingBoxInstance3);
        assertNotNull(testNestedBoxInstance);

        // check test-linux-box instance
        assertTrue(MessageFormat.format("Instance {0} is not terminated", Client.getPageUrl(cloud.getEndpointUrl(), testLinuxBoxInstance)),
                Client.TERMINATE_OPERATIONS.contains(testLinuxBoxInstance.getJSONObject("operation").getString("event")));
        JSONArray variables = testLinuxBoxInstance.getJSONArray("variables");
        assertEquals(1, TestUtils.findVariable(variables, "ANY_BINDING").getJSONArray("tags").size());
        assertTrue(TestUtils.findVariable(variables, "ANY_BINDING").getJSONArray("tags").getString(0).equals(TestUtils.TEST_BINDING_INSTANCE_TAG));

        assertEquals(MessageFormat.format("SLAVE_HOST_NAME: {0}", variableResolver.resolve("${SLAVE_HOST_NAME}")),
                TestUtils.findVariable(variables, "VAR_INSIDE").getString("value"));
        assertNull(TestUtils.findVariable(variables, "VAR_WHOLE"));

        // check test-nested-box instance
        assertTrue(MessageFormat.format("Instance {0} is not on-line", Client.getPageUrl(cloud.getEndpointUrl(), testNestedBoxInstance)),
                Client.ON_OPERATIONS.contains(testNestedBoxInstance.getJSONObject("operation").getString("event")) &&
                        !Client.InstanceState.UNAVAILABLE.equals(testNestedBoxInstance.getString("state")));
        variables = testNestedBoxInstance.getJSONArray("variables");
        assertEquals(1, TestUtils.findVariable(variables, "ANY_BINDING", "nested").getJSONArray("tags").size());
        assertEquals(1, TestUtils.findVariable(variables, "BINDING").getJSONArray("tags").size());
        assertEquals(testTag, TestUtils.findVariable(variables, "VAR_INSIDE").getString("value"));
        assertEquals(testTag, TestUtils.findVariable(variables, "VAR_WHOLE").getString("value"));
        assertEquals(testTag, TestUtils.findVariable(variables, "VAR_INSIDE", "nested").getString("value"));
        assertNull(TestUtils.findVariable(variables, "HTTP", "nested"));
        JSONObject variable = TestUtils.findVariable(variables, "VAR_FILE");
        Assert.assertNotNull(variable);
        Assert.assertNotNull("VAR_FILE is not updated", variable.getString("value"));
        File file = File.createTempFile(testTag, null);
        FileOutputStream fileOutput = new FileOutputStream(file);
        try {
            client.writeTo(variable.getString("value"), fileOutput);
        } finally {
            fileOutput.close();;
        }
        FileUtils.contentEquals(file, testNestedBoxJsonFile);
        variable = TestUtils.findVariable(variables, "VAR_FILE", "nested");
        Assert.assertNotNull(variable);
        Assert.assertNotNull("VAR_FILE is not updated", variable.getString("value"));
        fileOutput = new FileOutputStream(file);
        try {
            client.writeTo(variable.getString("value"), fileOutput);
        } finally {
            fileOutput.close();;
        }
        FileUtils.contentEquals(file, jenkinsImageFile);

        TestUtils.cleanUp(testTag, jenkins.getInstance());
    }

}
