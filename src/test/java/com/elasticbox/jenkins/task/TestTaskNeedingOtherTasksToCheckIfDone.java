package com.elasticbox.jenkins.task;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

/**
 * Created by serna on 12/7/15.
 */
public class TestTaskNeedingOtherTasksToCheckIfDone extends TestComplexTaskBase{

    @Test
    public void testTaskNeedingOtherTasksToCheckIfDoneAllOK() throws InterruptedException, TaskException {

        final ScheduledTask<Integer> scheduledTask = this.createFakeScheduledTask(1, 1, 7, 3, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTask(2);

        final TaskNeedingOtherTasksToCheckIfDoneExample task = new TaskNeedingOtherTasksToCheckIfDoneExample.Builder()
                .willTake(2)
                .withTimeout(10)
                .withTaskToCheckIfDone(simpleTask)
                .withTaskToCheckIfDone(scheduledTask)
                .build();

        task.execute();

        assertTrue("The task should be finished ok", task.isDone());
        assertTrue("The task should be finished ok", task.isChecked());

        assertTrue("The simple should be finished ok", simpleTask.isDone());
        assertTrue("The scheduled should be finished ok", scheduledTask.isDone());

        assertTrue("The task should be finished ok", task.getResult()==0);
        assertTrue("The simpleTask should be finished ok", simpleTask.getResult()==0);
        assertTrue("The scheduledTask should be finished ok", scheduledTask.getResult()==0);


    }

    @Test
    public void testTaskNeedingOtherTasksToCheckIfDoneWithOneTaskCheckingFailing() throws InterruptedException, TaskException {


        final ScheduledTask<Integer> scheduledTask = this.createFakeScheduledTask(1, 1, 7, 3, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTaskThrowingExceptionDuringExecution();

        final TaskNeedingOtherTasksToCheckIfDoneExample task = new TaskNeedingOtherTasksToCheckIfDoneExample.Builder()
                .willTake(2)
                .withTimeout(10)
                .withTaskToCheckIfDone(simpleTask)
                .withTaskToCheckIfDone(scheduledTask)
                .build();

        task.execute();

        assertTrue("The task should be finished ok", task.isDone());
        assertTrue("The task should be checked NO ok", !task.isChecked());

        assertTrue("The simple should be finished NO ok", !simpleTask.isDone());
        assertTrue("The scheduled should be finished ok", scheduledTask.isDone());

        assertTrue("The task should be finished ok", task.getResult()==0);
        assertTrue("The simpleTask should be finished ok", simpleTask.getResult()==1);
        assertTrue("The scheduledTask should be finished ok", scheduledTask.getResult()==0);


    }

    @Test
    public void testTaskNeedingOtherTasksToCheckIfDoneWithTimeout() throws InterruptedException, TaskException {

        //this task wil reach the timeout before completion
        final ScheduledTask<Integer> scheduledTask = this.createFakeScheduledTask(1, 1, 7, 8, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTask(2);

        final TaskNeedingOtherTasksToCheckIfDoneExample task = new TaskNeedingOtherTasksToCheckIfDoneExample.Builder()
                .willTake(2)
                .withTimeout(10)
                .withTaskToCheckIfDone(simpleTask)
                .withTaskToCheckIfDone(scheduledTask)
                .build();

        task.execute();

        assertTrue("The task should be finished ok", task.isDone());
        assertTrue("The task should be checked NO ok", !task.isChecked());

        assertTrue("The simple should be finished NO ok", simpleTask.isDone());
        assertTrue("The scheduled should be finished ok", !scheduledTask.isDone());

        assertTrue("The task should be finished ok", task.getResult()==0);
        assertTrue("The simpleTask should be finished ok", simpleTask.getResult()==0);
        assertTrue("The scheduledTask should be finished ok", scheduledTask.getResult()==1);


    }

    @Test(expected=TaskException.class)
    public void testTaskNeedingOtherTasksToCheckIfDoneErrorInMainTask() throws InterruptedException, TaskException {

        final ScheduledTask<Integer> scheduledTask = this.createFakeScheduledTask(1, 1, 5, 3, 100);

        final Task<Integer> simpleTask = this.createFakeSimpleTaskThrowingExceptionDuringExecution();

        final TaskNeedingOtherTasksToCheckIfDoneExample task = new TaskNeedingOtherTasksToCheckIfDoneExample.Builder()
                .willTake(2)
                .withException(true)
                .withTimeout(10)
                .withTaskToCheckIfDone(simpleTask)
                .withTaskToCheckIfDone(scheduledTask)
                .build();

        task.execute();

    }
}
