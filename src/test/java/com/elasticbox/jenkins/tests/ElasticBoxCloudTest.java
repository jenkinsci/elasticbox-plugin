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
import com.elasticbox.ClientException;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.Scrambler;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxCloudTest extends HudsonTestCase {
    private static final String JENKINS_SLAVE_BOX_NAME = "test-linux-jenkins-slave";
    private static final String PUBLIC_JENKINS_HOST = "localhost";
    private static final boolean TEST_BUILD = Boolean.getBoolean("elasticbox.jenkins.test.build");
    private static final String ELASTICBOX_URL = System.getProperty("elasticbox.jenkins.test.ElasticBoxURL", "https://catapult.elasticbox.com");
    private static final String USER_NAME = System.getProperty("elasticbox.jenkins.test.username", Scrambler.descramble("dHBob25naW9AZ21haWwuY29t"));
    private static final String PASSWORD = System.getProperty("elasticbox.jenkins.test.password", Scrambler.descramble("dHBob25naW8="));
    private static final String LAUNCH_BOX_NAME = System.getProperty("elasticbox.jenkins.test.launchBox", "test-linux-box");

    public void testConfigRoundtrip() throws Exception {
        ElasticBoxCloud cloud = new ElasticBoxCloud("https://elasticbox.com", 2, 10, USER_NAME, PASSWORD);
        jenkins.clouds.add(cloud);
        
        WebClient webClient = createWebClient();
        HtmlForm configForm = webClient.goTo("configure").getFormByName("config");
        submit(webClient.goTo("configure").getFormByName("config"));        
        assertEqualBeans(cloud, jenkins.clouds.iterator().next(), "endpointUrl,maxInstances,retentionTime,username,password");
        
        configForm.submit(configForm.getButtonByCaption("Test Connection"));

        // test connection
        PostMethod post = new PostMethod(MessageFormat.format("{0}descriptorByName/{1}/testConnection?.crumb=test", jenkins.getRootUrl(), ElasticBoxCloud.class.getName()));        
        post.setRequestBody(Arrays.asList(new NameValuePair("endpointUrl", cloud.getEndpointUrl()),
                new NameValuePair("username", cloud.getUsername()),
                new NameValuePair("password", cloud.getPassword())).toArray(new NameValuePair[0]));
        HttpClient httpClient = new HttpClient();
        int status = httpClient.executeMethod(post);
        String content = post.getResponseBodyAsString();
        assertEquals(HttpStatus.SC_OK, status);
        assertStringContains(content, content, MessageFormat.format("Connection to {0} was successful.", cloud.getEndpointUrl()));
        
        jenkins.createProjectFromXML("test", getClass().getResourceAsStream("TestProject.xml"));
        
        testClient(cloud);   
    }
    
    public void testBuild() throws Exception { 
        if (TEST_BUILD) {
            ElasticBoxCloud cloud = new ElasticBoxCloud(ELASTICBOX_URL, 2, 10, USER_NAME, PASSWORD);
            jenkins.clouds.add(cloud);
            testBuildWithSteps(cloud);
        }
    }
    
    private void testClient(ElasticBoxCloud cloud) throws Exception {
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        client.connect();
        JSONArray workspaces = client.getWorkspaces();
        JSONObject personalWorkspace = null;
        for (Object workspace : workspaces) {
            if (((JSONObject) workspace).getString("email") != null) {
                personalWorkspace = (JSONObject) workspace;
                break;
            }
        }
        assertNotNull(personalWorkspace);
        JSONArray boxes = client.getBoxes(personalWorkspace.getString("id"));
        JSONObject testJenkinsSlaveBox = null;
        for (Object box : boxes) {
            JSONObject boxJson = (JSONObject) box;
            if (boxJson.getString("name").equals(JENKINS_SLAVE_BOX_NAME)) {
                testJenkinsSlaveBox = boxJson;
                break;
            }
        }
        assertNotNull(testJenkinsSlaveBox);
        JSONArray profiles = client.getProfiles(personalWorkspace.getString("id"), testJenkinsSlaveBox.getString("id"));
        assertTrue(profiles.size() > 0);
        for (Object profile : profiles) {
            JSONObject profileJson = client.getProfile(((JSONObject) profile).getString("id"));
            assertEquals(profile.toString(), profileJson.toString());
        }        
        
        // make sure that a deployment request can be successfully submitted
        JSONObject profile = profiles.getJSONObject(0);
        Map<String, String> variables = new HashMap<String, String>();
        variables.put("JENKINS_URL", jenkins.getRootUrl().replace("localhost", PUBLIC_JENKINS_HOST));
        variables.put("SLAVE_NAME", JENKINS_SLAVE_BOX_NAME);
        try {
            client.deploy(profile.getString("id"), profile.getString("owner"), "test", 1, variables);
        } catch (ClientException ex) {
            assertEquals(ex.getMessage(), HttpStatus.SC_BAD_REQUEST, ex.getStatusCode());
            assertStringContains(ex.getMessage(), profile.getString("provider"));
        }        
    }
    
    private void testBuildWithSteps(ElasticBoxCloud cloud) throws Exception {        
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONArray profiles = (JSONArray) client.doGet(MessageFormat.format("/services/profiles?box_name={0}", LAUNCH_BOX_NAME), true);
        assertTrue(MessageFormat.format("No profile is found for box {0} for {1}", LAUNCH_BOX_NAME, ElasticBoxCloud.getInstance().name), profiles.size() > 0);
        JSONObject profile = profiles.getJSONObject(0);
        
        // deploy a test instance for build steps
        IProgressMonitor monitor = client.deploy(profile.getString("id"), profile.getString("owner"), "jenkins-plugin-instance-events", 1, Collections.EMPTY_MAP);
        monitor.waitForDone(60);
        String instanceId = Client.getResourceId(monitor.getResourceUrl());
        
        String projectXml = IOUtils.toString((InputStream) getClass().getResource("TestProject.xml").getContent());
        projectXml = projectXml.replace("{workspaceId}", profile.getString("owner")).
                replace("{LaunchBox.boxId}", profile.getJSONObject("box").getString("version")).
                replace("{LaunchBox.profileId}", profile.getString("id")).
                replace("{instanceId}", instanceId);
        
        FreeStyleProject project = (FreeStyleProject) jenkins.createProjectFromXML("test", new ByteArrayInputStream(projectXml.getBytes()));
        QueueTaskFuture future = project.scheduleBuild2(0);
        Future startCondition = future.getStartCondition();
        startCondition.get(60, TimeUnit.MINUTES);
        FreeStyleBuild result = (FreeStyleBuild) future.get(60, TimeUnit.MINUTES);
        System.out.println(result.getTestResultAction().getResult());
    }
    
    private void testBuildWithSlave(ElasticBoxCloud cloud) throws Exception {
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONArray profiles = (JSONArray) client.doGet(MessageFormat.format("/services/profiles?box_name={0}", JENKINS_SLAVE_BOX_NAME), true);
        assertTrue(MessageFormat.format("No profile is found for box {0} for {1}", JENKINS_SLAVE_BOX_NAME, ElasticBoxCloud.getInstance().name), profiles.size() > 0);
        JSONObject profile = profiles.getJSONObject(0);        
        String projectXml = IOUtils.toString((InputStream) getClass().getResource("TestProjectWithSlave.xml").getContent());
        projectXml = projectXml.replace("{workspaceId}", profile.getString("owner")).
                replace("{InstanceCreator.boxId}", profile.getJSONObject("box").getString("version")).
                replace("{InstanceCreator.profileId}", profile.getString("id")).
                replace("{version}", "0.7.5-SNAPSHOT");   
        FreeStyleProject project = (FreeStyleProject) jenkins.createProjectFromXML("test", new ByteArrayInputStream(projectXml.getBytes()));
        QueueTaskFuture future = project.scheduleBuild2(0);
        Future startCondition = future.getStartCondition();
        startCondition.get(60, TimeUnit.MINUTES);
        Object result = future.get(60, TimeUnit.MINUTES);        
    }
}
