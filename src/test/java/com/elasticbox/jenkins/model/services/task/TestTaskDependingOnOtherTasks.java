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

package com.elasticbox.jenkins.model.services.task;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by serna on 12/7/15.
 */
public class TestTaskDependingOnOtherTasks extends TestComplexTaskBase{

    @Test
    public void testTaskNeedingOtherTasksToCheckIfDoneAllOK() throws InterruptedException, TaskException {

        final ScheduledPoolingTask<Integer> scheduledPoolingTask = this.createFakeScheduledPoolingTask(1, 1, 7, 3, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTask(2);

        final TaskDependingOnOtherTasksExample task = new TaskDependingOnOtherTasksExample.Builder()
                .willTake(2)
                .withTimeout(10)
                .withDependingTask(simpleTask)
                .withDependingTask(scheduledPoolingTask)
                .build();

        task.execute();

        assertTrue("The task should be finished ok", task.isDone());
        assertTrue("The task should be finished ok", task.isChecked());

        assertTrue("The simple should be finished ok", simpleTask.isDone());
        assertTrue("The scheduled should be finished ok", scheduledPoolingTask.isDone());

        assertTrue("The task should be finished ok", task.getResult()==0);
        assertTrue("The simpleTask should be finished ok", simpleTask.getResult()==0);
        assertTrue("The scheduledPoolingTask should be finished ok", scheduledPoolingTask.getResult()==0);


    }

    @Test
    public void testTaskNeedingOtherTasksToCheckIfDoneWithOneTaskCheckingFailing() throws InterruptedException, TaskException {


        final ScheduledPoolingTask<Integer> scheduledPoolingTask = this.createFakeScheduledPoolingTask(1, 1, 7, 3, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTaskThrowingExceptionDuringExecution();

        final TaskDependingOnOtherTasksExample task = new TaskDependingOnOtherTasksExample.Builder()
                .willTake(2)
                .withTimeout(10)
                .withDependingTask(simpleTask)
                .withDependingTask(scheduledPoolingTask)
                .build();

        task.execute();

        assertTrue("The task should be finished ok", task.isDone());
        assertTrue("The task should be checked NO ok", !task.isChecked());

        assertTrue("The simple should be finished NO ok", !simpleTask.isDone());
        assertTrue("The scheduled should be finished ok", scheduledPoolingTask.isDone());

        assertTrue("The task should be finished ok", task.getResult()==0);
        assertTrue("The simpleTask should be finished ok", simpleTask.getResult()==1);
        assertTrue("The scheduledPoolingTask should be finished ok", scheduledPoolingTask.getResult()==0);


    }

    @Test
    public void testTaskNeedingOtherTasksToCheckIfDoneWithTimeout() throws InterruptedException, TaskException {

        //this task wil reach the timeout before completion
        final ScheduledPoolingTask<Integer> scheduledPoolingTask = this.createFakeScheduledPoolingTask(1, 1, 7, 8, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTask(2);

        final TaskDependingOnOtherTasksExample task = new TaskDependingOnOtherTasksExample.Builder()
                .willTake(2)
                .withTimeout(10)
                .withDependingTask(simpleTask)
                .withDependingTask(scheduledPoolingTask)
                .build();

        task.execute();

        assertTrue("The task should be finished ok", task.isDone());
        assertTrue("The task should be checked NO ok", !task.isChecked());

        assertTrue("The simple should be finished NO ok", simpleTask.isDone());
        assertTrue("The scheduled should be finished ok", !scheduledPoolingTask.isDone());

        assertTrue("The task should be finished ok", task.getResult()==0);
        assertTrue("The simpleTask should be finished ok", simpleTask.getResult()==0);
        assertTrue("The scheduledPoolingTask should be finished ok", scheduledPoolingTask.getResult()==1);


    }

    @Test(expected=TaskException.class)
    public void testTaskNeedingOtherTasksToCheckIfDoneErrorInMainTask() throws InterruptedException, TaskException {

        final ScheduledPoolingTask<Integer> scheduledPoolingTask = this.createFakeScheduledPoolingTask(1, 1, 5, 3, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTaskThrowingExceptionDuringExecution();

        final TaskDependingOnOtherTasksExample task = new TaskDependingOnOtherTasksExample.Builder()
                .willTake(2)
                .withException(true)
                .withTimeout(10)
                .withDependingTask(simpleTask)
                .withDependingTask(scheduledPoolingTask)
                .build();

        task.execute();

    }
}
