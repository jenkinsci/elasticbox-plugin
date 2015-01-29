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

import com.elasticbox.Client;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class BoxVersionTest extends BuildStepTestBase {
    private String testTag;
    private String testLinuxBoxVersion1;
    private String testLinuxBoxVersion2;
    
    private final String STAGING = "staging";
    
    
    private JSONObject createVersion(JSONObject box, String versionDescription) throws IOException {
        JSONObject boxCopy = JSONObject.fromObject(box);
        JSONObject version = new JSONObject();
        version.put("box", boxCopy.get("id"));
        version.put("description", versionDescription);
        boxCopy.put("version", version);        
        return cloud.getClient().doUpdate(boxCopy.getString("uri"), boxCopy);        
    }
    
    private void share(String resourceUri, String workspace, boolean readOnly) throws IOException {
        Client client = cloud.getClient();
        JSONObject resource = (JSONObject) client.doGet(resourceUri, false);        
        JSONArray members = resource.getJSONArray("members");
        for (Iterator iter = members.iterator(); iter.hasNext();) {
            JSONObject memberJson = (JSONObject) iter.next();
            if (memberJson.getString("workspace").equals(workspace)) {
                iter.remove();
                break;
            }
        }
        JSONObject member = new JSONObject();
        member.put("workspace", workspace);
        member.put("role", readOnly ? "read" : "collaborator");
        members.add(member);                    
        resource.put("members", members);
        client.doUpdate(resourceUri, resource);
    }

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        
        testTag = UUID.randomUUID().toString().substring(0, 30);
        
        // create staging workspace if it is not there
        Client client = cloud.getClient();
        boolean stagingWorkspaceFound = false;
        for (Object workspace : client.getWorkspaces()) {
            JSONObject workspaceJson = (JSONObject) workspace;
            if (workspaceJson.getString("id").equals(STAGING)) {
                stagingWorkspaceFound = true;
                break;
            }
        }
        if (!stagingWorkspaceFound) {
            client.createWorkspace(STAGING);
        }
        
        // create versions for the box test-linux-box to test
        JSONObject testLinuxBox = testBoxDataLookup.get("test-linux-box").getJson();
        JSONObject testLinuxBoxVersion = createVersion(testLinuxBox, "1");
        testLinuxBoxVersion1 = testLinuxBoxVersion.getString("id");

        // add new variable to test-linux-box and share it as read-only with staging workspace        
        JSONObject newVariable = new JSONObject();
        newVariable.put("name", "NEW_VAR");
        newVariable.put("type", "Text");
        newVariable.put("value", "NEW_VAR");
        testLinuxBox.getJSONArray("variables").add(newVariable);
        client.doUpdate(testLinuxBox.getString("uri"), testLinuxBox);  
        share(testLinuxBox.getString("uri"), STAGING, true);
        
        share(testBoxDataLookup.get("test-nested-box").getJson().getString("uri"), STAGING, false);
        share(testProvider.getString("uri"), STAGING, true);
    }  
    
    @After
    @Override
    public void tearDown() throws Exception {
        TestUtils.cleanUp(testTag, STAGING, jenkins.getInstance());
        super.tearDown();
    }
        
    
    private void checkBuildOutcome(AbstractBuild build, String expectedBoxVersion, String expectedChildBoxVersion) throws Exception {
        // check that the latest of test-linux-box is deployed and updated by the build
        Client client = cloud.getClient();
        JSONObject testInstance = TestUtils.findInstance(client.getInstances(STAGING), testTag);
        Assert.assertNotNull("Cannot find instance deployed by the build", testInstance);
        // fetch the whole instance object
        testInstance = client.getInstance(testInstance.getString("id"));
        JSONArray instanceBoxes = testInstance.getJSONArray("boxes");
        JSONObject mainBox = instanceBoxes.getJSONObject(0);
        JSONObject nestedBox = instanceBoxes.getJSONObject(1);
        Assert.assertEquals(expectedBoxVersion, mainBox.getString("id"));
        Assert.assertEquals(expectedChildBoxVersion, nestedBox.getString("id"));        
        JSONArray variables = testInstance.getJSONArray("variables");
        JSONObject newVariable = TestUtils.findVariable(variables, "NEW_VAR", "nested");        
        if (expectedChildBoxVersion.equals(testBoxDataLookup.get("test-linux-box").getJson().getString("id")) ||
                expectedChildBoxVersion.equals(testLinuxBoxVersion2)) {
            Assert.assertNotNull(MessageFormat.format("Variable nested.NEW_VAR was not found", newVariable));            
        } else if (expectedChildBoxVersion.equals(testLinuxBoxVersion1)) {
            Assert.assertNull(MessageFormat.format("Unexpected variable: {0}", newVariable), newVariable);
        }
        JSONObject updatedVariable = TestUtils.findVariable(variables, "VAR_WHOLE", "nested");
        Assert.assertNotNull("Variable VAR_WHOLE was not updated", updatedVariable);
        VariableResolver resolver = new VariableResolver(build, TaskListener.NULL);
        Assert.assertEquals(resolver.resolve("${BUILD_NUMBER}"), updatedVariable.getString("value"));
    }
    
    @Test
    public void testBoxVersion() throws Exception {
        Map<String, String> parameters = Collections.singletonMap("TEST_TAG", testTag);        
        FreeStyleProject project = TestUtils.createProject("test", createTestDataFromTemplate("jobs/test-box-version.xml"), jenkins.getInstance());
        FreeStyleBuild build = TestUtils.runJob(project, parameters, jenkins.getInstance());
        TestUtils.assertBuildSuccess(build);

        JSONObject testLinuxBox = testBoxDataLookup.get("test-linux-box").getJson();
        JSONObject testNestedBox = testBoxDataLookup.get("test-nested-box").getJson();
        String testNestedBoxUri = testNestedBox.getString("uri");
        String testNestedBoxId = testNestedBox.getString("id");
        
        // check that the latest of test-nested-box is deployed and updated by the build
        checkBuildOutcome(build, testNestedBoxId, testLinuxBoxVersion1);
        TestUtils.cleanUp(testTag, STAGING, jenkins.getInstance());
        
        // create version 1 of test-nested-box, share it as read-only with staging workspace and build again
        String testNestedBoxVersion1 = createVersion(testNestedBox, "1").getString("id");
        share(testNestedBoxUri, STAGING, true);
        build = TestUtils.runJob(project, parameters, jenkins.getInstance());
        TestUtils.assertBuildSuccess(build);        
        // check that version 1 is deployed and child box test-linux-box is updated by the build
        checkBuildOutcome(build, testNestedBoxVersion1, testLinuxBoxVersion1);     
        TestUtils.cleanUp(testTag, STAGING, jenkins.getInstance());
        
        // create version 2 of test-linux-box and build again
        testLinuxBoxVersion2 = createVersion(testLinuxBox, "2").getString("id");
        build = TestUtils.runJob(project, parameters, jenkins.getInstance());
        TestUtils.assertBuildSuccess(build);        
        // check that version 2 is deployed and updated by the build
        checkBuildOutcome(build, testNestedBoxVersion1, testLinuxBoxVersion2); 
    }
}
