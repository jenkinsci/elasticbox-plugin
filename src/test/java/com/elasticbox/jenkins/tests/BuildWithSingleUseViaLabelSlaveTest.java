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
import com.elasticbox.jenkins.SlaveConfiguration;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.api.BoxRepositoryApiImpl;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class BuildWithSingleUseViaLabelSlaveTest extends SlaveBuildTestBase {

    @Test
    public void testBuildWithSingleUseSlaveViaLabel() throws Exception {
        final String slaveBoxName = TestUtils.JENKINS_SLAVE_BOX_NAME;
        LOGGER.info(MessageFormat.format("Testing build with single use slave deployed from box {0}", slaveBoxName));
        Client client = cloud.getClient();
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

        final List<PolicyBox> policies = new BoxRepositoryApiImpl(client).getNoCloudFormationPolicyBoxes(workspace);

        TestCase.assertTrue(MessageFormat.format("No profile is found for box {0} of ElasticBox cloud {1}", slaveBoxName, cloud.getDisplayName()), policies.size() > 0);

        String label = UUID.randomUUID().toString();

        // Create a slave configuration with 0 retention time. This means, the slave of this configuration will be killed right after use (single-use)
        final PolicyBox policyBox = policies.get(0);
        SlaveConfiguration slaveConfig = new SlaveConfiguration(UUID.randomUUID().toString(), workspace, boxId, boxId,
                policyBox.getId(), null, null, null, 0, 1, slaveBoxName, "[]", label, "", null, Node.Mode.NORMAL, 0, null, 1, 60, DeploymentType.SCRIPTBOX_DEPLOYMENT_TYPE.getValue());

        ElasticBoxCloud newCloud = new ElasticBoxCloud("elasticbox-" + UUID.randomUUID().toString(), "ElasticBox", cloud.getEndpointUrl(), cloud.getMaxInstances(), cloud.getToken(), Collections.singletonList(slaveConfig));

        jenkins.getInstance().clouds.remove(cloud);
        jenkins.getInstance().clouds.add(newCloud);

        FreeStyleProject project = jenkins.getInstance().createProject(FreeStyleProject.class, MessageFormat.format("Build with {0}", slaveBoxName));
        project.setAssignedLabel(jenkins.getInstance().getLabel(label));
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
        Thread.sleep(TimeUnit.SECONDS.toMillis(120));
        Assert.assertNull("Single-use slave is not removed after build", findSlave(label));
    }

}
