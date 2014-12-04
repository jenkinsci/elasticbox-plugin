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
import com.elasticbox.jenkins.ElasticBoxSlave;
import com.elasticbox.jenkins.SingleUseSlaveBuildOption;
import com.elasticbox.jenkins.SlaveConfiguration;
import static com.elasticbox.jenkins.tests.SlaveTestBase.LOGGER;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class BuildWithProjectSingleUseSlaveTest extends SlaveTestBase {
    
    @Test
    public void testBuildWithProjectWithSingleUseSlaveOption() throws Exception {
        final String slaveBoxName = TestUtils.JENKINS_SLAVE_BOX_NAME;
        LOGGER.info(MessageFormat.format("Testing build with single use slave deployed from box {0}", slaveBoxName));
        ElasticBoxCloud ebCloud = createCloud();
        Client client = ebCloud.getClient();
        JSONObject slaveBox = null;
        String workspace = TestUtils.TEST_WORKSPACE;
        for (Object box : client.getBoxes(workspace)) {
            JSONObject boxJson = (JSONObject) box;
            if (slaveBoxName.equals(boxJson.getString("name"))) {
                slaveBox = boxJson;
                break;
            }
        }
        Assert.assertNotNull(MessageFormat.format("Cannot find slave box {0} in workspace {1}", slaveBoxName, workspace), slaveBox);
        String boxId = slaveBox.getString("id");
        JSONArray profiles = (JSONArray) client.doGet(MessageFormat.format("/services/workspaces/{0}/profiles?box_version={1}", 
                workspace, boxId), true);
        TestCase.assertTrue(MessageFormat.format("No profile is found for box {0} of ElasticBox cloud {1}", slaveBoxName, ebCloud.getDisplayName()), profiles.size() > 0);
        JSONObject profile = profiles.getJSONObject(0);
        String label = UUID.randomUUID().toString();
        
        // Create a slave configuration with retention time of 2 minutes.
        SlaveConfiguration slaveConfig = new SlaveConfiguration(UUID.randomUUID().toString(), workspace, boxId, boxId, 
                profile.getString("id"), 0, 1, slaveBoxName, "[]", label, "", null, Node.Mode.NORMAL, 2, null, 1, 60);
        ElasticBoxCloud newCloud = new ElasticBoxCloud("elasticbox-" + UUID.randomUUID().toString(), "ElasticBox", ebCloud.getEndpointUrl(), ebCloud.getMaxInstances(), ebCloud.getToken(), Collections.singletonList(slaveConfig));
        jenkins.getInstance().clouds.remove(ebCloud);
        jenkins.getInstance().clouds.add(newCloud);
        
        // create a project with single-use slave option and tie it to the slave config created above
        FreeStyleProject project = jenkins.getInstance().createProject(FreeStyleProject.class, MessageFormat.format("Build with {0}", slaveBoxName));
        project.getBuildWrappersList().add(new SingleUseSlaveBuildOption());
        project.setAssignedLabel(jenkins.getInstance().getLabel(label));                        
        
        // schedule a build
        QueueTaskFuture future = project.scheduleBuild2(0);
        Object scheduleResult = TestUtils.getResult(future.getStartCondition(), 30);
        TestCase.assertNotNull("30 minutes after job scheduling but no result returned", scheduleResult);
        ElasticBoxSlave slave = findSlave(label);
        FreeStyleBuild result = (FreeStyleBuild) TestUtils.getResult(future, 10);
        TestCase.assertNotNull("10 minutes after job start but no result returned", result);
        TestCase.assertEquals(Result.SUCCESS, result.getResult());
        
        // check that slave can no longer accept task
        Assert.assertFalse("Single-use slave still can accept task even after the build is canceled", slave.getComputer().isAcceptingTasks());
        
        // check that slave is removed      
        Thread.sleep(TimeUnit.MINUTES.toMillis(5));
        Assert.assertNull("Single-use slave is not removed after build", findSlave(label));
    }
    
}
