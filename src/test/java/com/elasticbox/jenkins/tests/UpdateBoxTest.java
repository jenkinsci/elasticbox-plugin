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
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class UpdateBoxTest extends BuildStepTestBase {

    @Test
    public void testUpdateBox() throws Exception {
        FreeStyleProject project = (FreeStyleProject) jenkins.getInstance().createProjectFromXML("test",
                new ByteArrayInputStream(createTestDataFromTemplate("jobs/test-update-box.xml").getBytes()));

        // copy files for box file variables
        FilePath workspace = jenkins.getInstance().getWorkspaceFor(project);
        File testNestedBoxJsonFile = new File(workspace.getRemote(), "test-nested-box.json");
        File jenkinsImageFile = new File(workspace.getRemote(), "jenkins.png");
        FileUtils.copyURLToFile(TestUtils.class.getResource("boxes/test-nested-box.json"), testNestedBoxJsonFile);
        FileUtils.copyURLToFile(TestUtils.class.getResource("jenkins.png"), jenkinsImageFile);


        final String testTag = UUID.randomUUID().toString().substring(0, 30);
        Map<String, String> testParameters = Collections.singletonMap("TEST_TAG", testTag);
        FreeStyleBuild build = TestUtils.runJob(project, testParameters, jenkins.getInstance());
        TestUtils.assertBuildSuccess(build);

        // verify the box has been updated and the uploaded files are there
        String boxId = testBoxDataLookup.get("test-nested-box").getJson().getString("id");
        Client client = cloud.getClient();
        JSONArray variables = client.getBox(boxId).getJSONArray("variables");
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
