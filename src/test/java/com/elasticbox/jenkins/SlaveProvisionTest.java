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

package com.elasticbox.jenkins;

import com.elasticbox.Client;
import com.elasticbox.jenkins.tests.SlaveProvisionTestBase;
import com.elasticbox.jenkins.tests.TestUtils;
import com.elasticbox.jenkins.util.Condition;
import hudson.ExtensionList;
import net.sf.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.UUID;

public class SlaveProvisionTest extends SlaveProvisionTestBase {

    private ElasticBoxSlaveHandler elasticBoxSlaveHandlerMock;

    @Test
    public void testSlaveProvision() throws Exception {
        provisionSlaves();
    }

    @Test
    public void testSlaveProvisionFailingAttempts() throws Exception {
        ExtensionList<ElasticBoxExecutor.Workload> workloads = jenkins.getInstance().getExtensionList(ElasticBoxExecutor.Workload.class);
        for (ElasticBoxExecutor.Workload workload: workloads) {
            if (workload instanceof ElasticBoxSlaveHandler) {
                elasticBoxSlaveHandlerMock = Mockito.spy(ElasticBoxSlaveHandler.class);
                workloads.add(0, elasticBoxSlaveHandlerMock);
                workloads.remove(workload);
                break;
            }
        }

        JSONArray variables = new JSONArray();
        SlaveConfiguration testFailingSlaveConfig = createSlaveConfiguration("linux-jenkins-slave-failing", variables);

        ElasticBoxCloud testCloud = new ElasticBoxCloud("elasticbox-" + UUID.randomUUID().toString(), "ElasticBox",
                TestUtils.ELASTICBOX_URL, 6, TestUtils.ACCESS_TOKEN,
                Collections.singletonList(testFailingSlaveConfig) );
        jenkins.getInstance().clouds.add(testCloud);

        // First attempt:
        new Condition() {

            @Override
            public boolean satisfied() {
                return jenkins.getInstance().getNodes().size() == 1;
            }
        }.waitUntilSatisfied(20);

        Assert.assertEquals("Expected only one node", 1, jenkins.getInstance().getNodes().size() );
        ElasticBoxSlave slave1 = (ElasticBoxSlave) jenkins.getInstance().getNodes().get(0);

        // Final state:
        waitUntilFinished(120);

        Assert.assertEquals("Expected only one node at the end of the run", 1, jenkins.getInstance().getNodes().size() );
        ElasticBoxSlave slave3 = (ElasticBoxSlave) jenkins.getInstance().getNodes().get(0);
        Assert.assertEquals("Unexpected last slave state", Client.InstanceState.UNAVAILABLE, slave3.getInstanceState() );
        Assert.assertNotEquals("Initial slave object must not be the same than final slave object", slave1, slave3);
        Assert.assertTrue("Expected last slave not to be removable from cloud", !slave3.isRemovableFromCloud() );
        Assert.assertTrue("Expected last slave not to be deletable", !slave3.isDeletable() );

        Mockito.verify(elasticBoxSlaveHandlerMock, Mockito.times(ElasticBoxSlaveHandler.InstanceCreationRequest.MAX_ATTEMPTS-1))
                .resubmitRequest((ElasticBoxSlaveHandler.InstanceCreationRequest) Mockito.any());

        Thread.sleep(20000); // Ensure no other requests are resubmitted on next cycle:
        Mockito.verify(elasticBoxSlaveHandlerMock, Mockito.times(ElasticBoxSlaveHandler.InstanceCreationRequest.MAX_ATTEMPTS-1))
                .resubmitRequest(Mockito.any(ElasticBoxSlaveHandler.InstanceCreationRequest.class));

        deleteAfter(slave3.getInstance() );
    }

    public void waitUntilFinished(int waitSeconds) {
        new Condition() {

            @Override
            public boolean satisfied() {
                if (jenkins.getInstance().getNodes().size() == 1) {
                    ElasticBoxSlave slave = (ElasticBoxSlave) jenkins.getInstance().getNodes().get(0);
                    return !slave.isRemovableFromCloud();
                }
                return false;

            }
        }.waitUntilSatisfied(waitSeconds, "SlaveProvisionTest - waitUntilFinished");
    }
}
