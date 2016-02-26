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

package com.elasticbox.jenkins.model.services.deployment.execution.task;

import com.elasticbox.Client;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.DeploymentOrderRepository;
import com.elasticbox.jenkins.model.repository.InstanceRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.execution.context.AbstractBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.deployment.execution.order.AbstractDeployBoxOrder;
import com.elasticbox.jenkins.model.services.task.TaskException;
import com.elasticbox.jenkins.util.TaskLogger;
import hudson.model.BuildListener;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class TestCheckInstancesDeployment {

    @Test
    public void testInstancesDeployment() throws RepositoryException, TaskException, IOException, InterruptedException {

        final Client client = Mockito.mock(Client.class);
        final ElasticBoxCloud elasticBoxCloud = Mockito.mock(ElasticBoxCloud.class);
        when(elasticBoxCloud.getClient()).thenReturn(client);
        when(elasticBoxCloud.getEndpointUrl()).thenReturn("http://localhost:port/");

        final BuildListener buildListener = Mockito.mock(hudson.model.BuildListener.class);
        when(buildListener.getLogger()).thenReturn(new PrintStream(System.out));

        TaskLogger taskLogger = new TaskLogger(buildListener);


        final List<Instance> allProcessing = UnitTestingUtils.getFakeProcessingInstancesList();

        List<Instance> twoForFinish = UnitTestingUtils.getFakeDoneInstancesList();
        twoForFinish.remove(2);
        twoForFinish.remove(1);
        twoForFinish.add(allProcessing.get(1));
        twoForFinish.add(allProcessing.get(2));

        List<Instance> justOneToFinish = UnitTestingUtils.getFakeDoneInstancesList();
        justOneToFinish.remove(2);
        justOneToFinish.add(allProcessing.get(2));


        final InstanceRepository instanceRepository = Mockito.mock(InstanceRepository.class);
        when(instanceRepository.getInstances(any(String.class), any(String[].class)))
                .thenReturn(allProcessing) //first round
                .thenReturn(allProcessing) //second round
                .thenReturn(allProcessing) //third round
                .thenReturn(twoForFinish)  //FOUR here al least one instance is done
                .thenReturn(twoForFinish)  //FIVE round!!! here we will check
                .thenReturn(justOneToFinish)
                .thenReturn(justOneToFinish)
                .thenReturn(UnitTestingUtils.getFakeDoneInstancesList());

        final AbstractDeployBoxOrder deployBoxOrder = Mockito.mock(AbstractDeployBoxOrder.class);
        when(deployBoxOrder.getOwner())
                .thenReturn("FAKe_OWNER");

        final AbstractBoxDeploymentContext abstractBoxDeploymentContext = Mockito.mock(AbstractBoxDeploymentContext.class);
        when(abstractBoxDeploymentContext.getOrder()).thenReturn(deployBoxOrder);
        when(abstractBoxDeploymentContext.getInstanceRepository()).thenReturn(instanceRepository);
        when(abstractBoxDeploymentContext.getLogger()).thenReturn(taskLogger);
        when(abstractBoxDeploymentContext.getCloud()).thenReturn(elasticBoxCloud);

        final CheckInstancesDeployedTask task = new CheckInstancesDeployedTask(abstractBoxDeploymentContext, UnitTestingUtils.getFakeProcessingInstancesList(),  5, 0, 120);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    task.execute();
                } catch (TaskException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        while (!task.isDone()){
            Thread.sleep(200);
            if (task.getCounter() == 5){
                final List<Instance> result = task.getResult();
                if (result != null){
                    for (Instance instance : result) {
                        final Instance.State state = instance.getState();
                        if (state == Instance.State.DONE){
                            assertTrue("The task has one instance done but it isn't finished yet", !task.isDone());
                        }
                    }
                }
            }
        }

        final List<Instance> result = task.getResult();
        for (Instance instance : result) {
            assertTrue("All instance should be done", instance.getState() == Instance.State.DONE);
        }

        assertTrue("Also the task should be done too", task.isDone());

    }
}
