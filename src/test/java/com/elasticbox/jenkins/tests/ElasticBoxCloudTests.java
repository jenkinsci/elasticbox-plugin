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
import com.elasticbox.jenkins.ElasticBoxSlave;
import com.elasticbox.jenkins.util.SlaveInstance;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.model.Node;
import java.text.MessageFormat;
import java.util.Arrays;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jvnet.hudson.test.HudsonTestCase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxCloudTests extends HudsonTestCase {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxCloudTests.class.getName());

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        String jenkinsUrl = jenkins.getRootUrl();
        if (StringUtils.isBlank(jenkinsUrl)) {
            jenkinsUrl = createWebClient().getContextPath();
        }
        
        jenkinsUrl = jenkinsUrl.replace("localhost", TestUtils.JENKINS_PUBLIC_HOST);
        JenkinsLocationConfiguration.get().setUrl(jenkinsUrl);
    }
    
    

    @Override
    protected void tearDown() throws Exception {
        List<ElasticBoxSlave> slaves = new ArrayList<ElasticBoxSlave>();
        for (Node node : jenkins.getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getInstanceId() != null) {
                    try {
                        slave.terminate();
                        slaves.add(slave);
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "Error terminating slave", ex);
                    }
                }
            }
        }
        
        long maxWaitTime = 600000;
        long waitStart = System.currentTimeMillis();
        do {
            Thread.sleep(5000);
            for (Iterator<ElasticBoxSlave> iter = slaves.iterator(); iter.hasNext();) {
                ElasticBoxSlave slave = iter.next();               
                JSONObject instance = null;
                try {
                    instance = slave.getInstance();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Error fetching slave instance", ex);
                    iter.remove();
                    continue;
                }
                
                if ((instance != null && Client.FINISH_STATES.contains(instance.getString("state"))) || 
                        System.currentTimeMillis() - waitStart > maxWaitTime) {
                    try {
                        slave.delete();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "Error deleting slave", ex);
                    }
                    iter.remove();
                }
            }
        } while (!slaves.isEmpty());
        
        super.tearDown();
    }
    
    public void testClient() throws Exception {
        ElasticBoxCloud cloud = createCloud();
        testClient(cloud);   
    }
    
    private ElasticBoxCloud createCloud() throws IOException {
        return createCloud(TestUtils.ELASTICBOX_URL, TestUtils.USER_NAME, TestUtils.PASSWORD);
    }
    
    private ElasticBoxCloud createCloud(String endpointUrl, String username, String password) throws IOException {
        ElasticBoxCloud cloud = new ElasticBoxCloud("elasticbox", endpointUrl, 2, 10, username, password, Collections.EMPTY_LIST);
        jenkins.clouds.add(cloud);
        return cloud;        
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
            if (boxJson.getString("name").equals(TestUtils.JENKINS_SLAVE_BOX_NAME)) {
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
        JSONArray variables = SlaveInstance.createJenkinsVariables(jenkins.getRootUrl(), TestUtils.JENKINS_SLAVE_BOX_NAME);
        JSONObject variable = new JSONObject();
        variable.put("name", "JNLP_SLAVE_OPTIONS");
        variable.put("type", "Text");
        variable.put("value", MessageFormat.format("-jnlpUrl {0}/computer/{1}/slave-agent.jnlp", TestUtils.JENKINS_SLAVE_BOX_NAME));
        variables.add(variable);                        
        IProgressMonitor monitor = client.deploy(profile.getString("id"), profile.getString("owner"), 
                "jenkins-plugin-unit-test", 1, variables);
        try {
            monitor.waitForDone(60);
        } catch (IProgressMonitor.IncompleteException ex) {
            
        }
        
        String instanceId = Client.getResourceId(monitor.getResourceUrl());
        monitor = client.terminate(instanceId);
        monitor.waitForDone(60);
        client.delete(instanceId);
        try {
            client.getInstance(instanceId);
            throw new Exception(MessageFormat.format("Instance {0} was not deleted", instanceId));
        } catch (ClientException ex) {
            assertEquals(ex.getStatusCode(), HttpStatus.SC_NOT_FOUND);
        }
    }
    
    private void testConfigRoundtrip(ElasticBoxCloud cloud) throws Exception {
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
    }
    
}
