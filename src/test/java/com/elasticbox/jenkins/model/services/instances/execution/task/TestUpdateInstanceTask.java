/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.services.instances.execution.task;

import com.elasticbox.Client;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.repository.InstanceRepository;
import com.elasticbox.jenkins.model.services.task.ScheduledPoolingTask;
import com.elasticbox.jenkins.model.services.task.TaskException;
import com.elasticbox.jenkins.tests.TestUtils;
import com.elasticbox.jenkins.util.TaskLogger;
import hudson.model.BuildListener;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * Created by serna on 12/7/15.
 */
public class TestUpdateInstanceTask {

    @Test
    public void testUpdateInstanceHappyPath() throws InterruptedException, TaskException, IOException {

        final Client client = Mockito.mock(Client.class);
        when(client.getInstance(any(String.class)))
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //second round
                .thenReturn(UnitTestingUtils.getFakeDoneInstance());
        when(client.updateInstance(any(JSONObject.class), any(JSONArray.class), any(String.class)))
                .thenReturn(UnitTestingUtils.getFakeDoneInstance());

        final ElasticBoxCloud elasticBoxCloud = Mockito.mock(ElasticBoxCloud.class);
        when(elasticBoxCloud.getClient()).thenReturn(client);
        when(elasticBoxCloud.getEndpointUrl()).thenReturn("http://localhost:port/");

        final BuildListener buildListener = Mockito.mock(hudson.model.BuildListener.class);
        when(buildListener.getLogger()).thenReturn(new PrintStream(System.out));


        TaskLogger taskLogger = new TaskLogger(buildListener);

        final JSONObject fakeJsonInstance = UnitTestingUtils.getFakeProcessingInstance();

        final JSONArray fakeJsonResolvedVariables = UnitTestingUtils.getTwoBindingVariables();

        UpdateInstanceTask task = new UpdateInstanceTask(client, taskLogger, fakeJsonInstance, fakeJsonResolvedVariables, "resolvedBoxVersion", true, 2, 2, 60, 24, 2);

        task.execute();

        final JSONObject result = task.getResult();

        final int totalRoundtrips = task.getTotalRoundtrips();

        assertTrue("Should be done", result.getString("state").equals("done"));
        assertTrue("Should be 2 roudtrips", totalRoundtrips == 3);

    }

    @Test
    public void testUpdateInstanceFailingInUpdateRetaryingOK() throws InterruptedException, TaskException, IOException {

        final Client client = Mockito.mock(Client.class);

        when(client.getInstance(any(String.class)))
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 1 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 1 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //third checking 1 round
                .thenReturn(UnitTestingUtils.getFakeDoneInstance())
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 2 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 2 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //third checking 2 round
                .thenReturn(UnitTestingUtils.getFakeDoneInstance());

        when(client.updateInstance(any(JSONObject.class), any(JSONArray.class), any(String.class)))
                .thenThrow(new IOException("Instance cannot be updated in the middle of an operation"))
                .thenReturn(UnitTestingUtils.getFakeDoneInstance());

        final ElasticBoxCloud elasticBoxCloud = Mockito.mock(ElasticBoxCloud.class);
        when(elasticBoxCloud.getClient()).thenReturn(client);
        when(elasticBoxCloud.getEndpointUrl()).thenReturn("http://localhost:port/");

        final BuildListener buildListener = Mockito.mock(hudson.model.BuildListener.class);
        when(buildListener.getLogger()).thenReturn(new PrintStream(System.out));


        TaskLogger taskLogger = new TaskLogger(buildListener);

        final JSONObject fakeJsonInstance = UnitTestingUtils.getFakeProcessingInstance();

        final JSONArray fakeJsonResolvedVariables = UnitTestingUtils.getTwoBindingVariables();

        UpdateInstanceTask task = new UpdateInstanceTask(client, taskLogger, fakeJsonInstance, fakeJsonResolvedVariables, "resolvedBoxVersion", true, 2, 2, 60, 24, 2);

        task.execute();

        final JSONObject result = task.getResult();

        final int totalRoundtrips = task.getTotalRoundtrips();

        assertTrue("Should be done", result.getString("state").equals("done"));
        assertTrue("Should be 2 roudtrips", totalRoundtrips == 3);

    }

    @Test
    public void testUpdateInstanceFailingCheckingTheInstanceState() throws InterruptedException, TaskException, IOException {

        final Client client = Mockito.mock(Client.class);

        when(client.getInstance(any(String.class)))
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance())
                .thenThrow(new IOException("Exception reading instance state"))
                .thenThrow(new IOException("Exception reading instance state"))
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //third checking 1 round
                .thenReturn(UnitTestingUtils.getFakeDoneInstance())
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 2 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 2 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //third checking 2 round
                .thenReturn(UnitTestingUtils.getFakeDoneInstance());

        when(client.updateInstance(any(JSONObject.class), any(JSONArray.class), any(String.class)))
                .thenThrow(new IOException("Instance cannot be updated in the middle of an operation"))
                .thenReturn(UnitTestingUtils.getFakeDoneInstance());

        final ElasticBoxCloud elasticBoxCloud = Mockito.mock(ElasticBoxCloud.class);
        when(elasticBoxCloud.getClient()).thenReturn(client);
        when(elasticBoxCloud.getEndpointUrl()).thenReturn("http://localhost:port/");

        final BuildListener buildListener = Mockito.mock(hudson.model.BuildListener.class);
        when(buildListener.getLogger()).thenReturn(new PrintStream(System.out));


        TaskLogger taskLogger = new TaskLogger(buildListener);

        final JSONObject fakeJsonInstance = UnitTestingUtils.getFakeProcessingInstance();

        final JSONArray fakeJsonResolvedVariables = UnitTestingUtils.getTwoBindingVariables();

        UpdateInstanceTask task = new UpdateInstanceTask(client, taskLogger, fakeJsonInstance, fakeJsonResolvedVariables, "resolvedBoxVersion", true, 2, 2, 60, 24, 2);

        task.execute();

        final JSONObject result = task.getResult();

        final int totalRoundtrips = task.getTotalRoundtrips();

        assertTrue("Should be done", result.getString("state").equals("done"));
        assertTrue("Should be 2 roudtrips", totalRoundtrips == 3);

    }

    @Test(expected=TaskException.class)
    public void testUpdateInstanceFailingInTheEnd() throws InterruptedException, TaskException, IOException {

        final Client client = Mockito.mock(Client.class);

        when(client.getInstance(any(String.class)))
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 1 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 1 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //third checking 1 round
                .thenReturn(UnitTestingUtils.getFakeDoneInstance())
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 2 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //first checking 2 round
                .thenReturn(UnitTestingUtils.getFakeProcessingInstance()) //third checking 2 round
                .thenReturn(UnitTestingUtils.getFakeDoneInstance());

        when(client.updateInstance(any(JSONObject.class), any(JSONArray.class), any(String.class)))
                .thenThrow(new IOException("Instance cannot be updated in the middle of an operation"))
                .thenThrow(new IOException("Instance cannot be updated in the middle of an operation"));
        final ElasticBoxCloud elasticBoxCloud = Mockito.mock(ElasticBoxCloud.class);
        when(elasticBoxCloud.getClient()).thenReturn(client);
        when(elasticBoxCloud.getEndpointUrl()).thenReturn("http://localhost:port/");

        final BuildListener buildListener = Mockito.mock(hudson.model.BuildListener.class);
        when(buildListener.getLogger()).thenReturn(new PrintStream(System.out));


        TaskLogger taskLogger = new TaskLogger(buildListener);

        final JSONObject fakeJsonInstance = UnitTestingUtils.getFakeProcessingInstance();

        final JSONArray fakeJsonResolvedVariables = UnitTestingUtils.getTwoBindingVariables();

        UpdateInstanceTask task = new UpdateInstanceTask(client, taskLogger, fakeJsonInstance, fakeJsonResolvedVariables, "resolvedBoxVersion", true, 2, 2, 60, 24, 2);

        task.execute();
    }

}
