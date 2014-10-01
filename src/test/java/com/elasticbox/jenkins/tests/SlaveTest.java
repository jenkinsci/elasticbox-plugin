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
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.SlaveConfiguration;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;
import junit.framework.TestCase;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveTest extends TestBase {
    private static final Logger LOGGER = Logger.getLogger(SlaveTest.class.getName());
    
    private ElasticBoxCloud createCloud(String endpointUrl, String username, String password) throws IOException {
        ElasticBoxCloud ebCloud = new ElasticBoxCloud("elasticbox", endpointUrl, 2, 10, username, password, Collections.EMPTY_LIST);
        jenkins.getInstance().clouds.add(ebCloud);
        return ebCloud;        
    }
    
    public void testBuildWithProjectSpecificSlave() throws Exception {
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONArray profiles = (JSONArray) client.doGet(MessageFormat.format("/services/profiles?box_name={0}", TestUtils.JENKINS_SLAVE_BOX_NAME), true);
        assertTrue(MessageFormat.format("No profile is found for box {0} of ElasticBox cloud {1}", TestUtils.JENKINS_SLAVE_BOX_NAME, cloud.getDisplayName()), profiles.size() > 0);
        JSONObject profile = profiles.getJSONObject(0);        
        String projectXml = IOUtils.toString((InputStream) getClass().getResource("TestProjectWithSlave.xml").getContent());
        projectXml = projectXml.replace("{workspaceId}", profile.getString("owner")).
                replace("{InstanceCreator.boxId}", profile.getJSONObject("box").getString("version")).
                replace("{InstanceCreator.profileId}", profile.getString("id")).
                replace("{version}", "0.7.5-SNAPSHOT");   
        FreeStyleProject project = (FreeStyleProject) jenkins.getInstance().createProjectFromXML("test", new ByteArrayInputStream(projectXml.getBytes()));
        QueueTaskFuture future = project.scheduleBuild2(0);
        Object scheduleResult = TestUtils.getResult(future.getStartCondition(), 30);
        assertNotNull("30 minutes after job scheduling but no result returned", scheduleResult);
        FreeStyleBuild result = (FreeStyleBuild) TestUtils.getResult(future, 60);      
        assertNotNull("60 minutes after job start but no result returned", result);
        assertEquals(Result.SUCCESS, result.getResult());
    }
    
    public void testBuildWithLinuxSlave() throws Exception {
        if (System.getProperty(TestUtils.OPS_USER_NAME_PROPERTY) != null) {
            testBuildWithSlave(TestUtils.JENKINS_SLAVE_BOX_NAME);
        }
    }

    public void testBuildWithWindowsSlave() throws Exception {
        if (System.getProperty(TestUtils.OPS_PASSWORD_PROPERTY) != null) {
            testBuildWithSlave("Windows Jenkins Slave");
        }
    }    

    private void testBuildWithSlave(String slaveBoxName) throws Exception {
        LOGGER.info(MessageFormat.format("Testing build with slave {0}", slaveBoxName));
        String username = System.getProperty(TestUtils.OPS_USER_NAME_PROPERTY);
        String password = System.getProperty(TestUtils.OPS_PASSWORD_PROPERTY);
        TestCase.assertNotNull(MessageFormat.format("System property {0} must be specified to run this test", TestUtils.OPS_USER_NAME_PROPERTY), username);
        TestCase.assertNotNull(MessageFormat.format("System property {0} must be specified to run this test", TestUtils.OPS_PASSWORD_PROPERTY), password);
        ElasticBoxCloud ebCloud = createCloud(TestUtils.ELASTICBOX_URL, username, password);
        Client client = new Client(ebCloud.getEndpointUrl(), ebCloud.getUsername(), ebCloud.getPassword());
        JSONArray profiles = (JSONArray) client.doGet(MessageFormat.format("/services/profiles?box_name={0}", URLEncoder.encode(slaveBoxName, "UTF-8")), true);
        TestCase.assertTrue(MessageFormat.format("No profile is found for box {0} of ElasticBox cloud {1}", slaveBoxName, ebCloud.getDisplayName()), profiles.size() > 0);
        JSONObject profile = profiles.getJSONObject(0);
        String workspace = profile.getString("owner");
        String box = profile.getJSONObject("box").getString("version");
        String label = UUID.randomUUID().toString();
        SlaveConfiguration slaveConfig = new SlaveConfiguration(UUID.randomUUID().toString(), workspace, box, box, profile.getString("id"), 0, 1, slaveBoxName, "[]", label, "", null, Node.Mode.NORMAL, 0, 1, 60);
        ElasticBoxCloud newCloud = new ElasticBoxCloud("elasticbox", ebCloud.getEndpointUrl(), ebCloud.getMaxInstances(), ebCloud.getRetentionTime(), ebCloud.getUsername(), ebCloud.getPassword(), Collections.singletonList(slaveConfig));
        jenkins.getInstance().clouds.remove(ebCloud);
        jenkins.getInstance().clouds.add(newCloud);
        FreeStyleProject project = jenkins.getInstance().createProject(FreeStyleProject.class, MessageFormat.format("Build with {0}", slaveBoxName));
        project.setAssignedLabel(jenkins.getInstance().getLabel(label));
        QueueTaskFuture future = project.scheduleBuild2(0);
        Object scheduleResult = TestUtils.getResult(future.getStartCondition(), 60);
        TestCase.assertNotNull("60 minutes after job scheduling but no result returned", scheduleResult);
        FreeStyleBuild result = (FreeStyleBuild) TestUtils.getResult(future, 30);
        TestCase.assertNotNull("30 minutes after job start but no result returned", result);
        TestCase.assertEquals(Result.SUCCESS, result.getResult());
    }
        
}
