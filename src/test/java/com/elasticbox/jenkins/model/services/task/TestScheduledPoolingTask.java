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

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class TestScheduledPoolingTask {

    @Test
    public void testScheduledTaskExecutionEndingInTime() throws InterruptedException, TaskException {

        final ScheduledPoolingTask<Integer> task = new ScheduledPoolingTask<Integer>(2, 2, 13) {
            private AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Integer getResult() {
                return counter.get();
            }

            @Override
            protected void performExecute() throws TaskException {
                System.out.println("Execution "+counter);
                counter.incrementAndGet();
            }

            @Override
            public boolean isDone() {
                return counter.get()==5;
            }

        };

        task.execute();


        assertTrue("Number of execution should match", task.getResult() == 5);

    }

    @Test(expected=TaskException.class)
    public void testScheduledTaskExecutionEndingWithTimeoutException() throws InterruptedException, TaskException {

        final ScheduledPoolingTask<Integer> task = new ScheduledPoolingTask<Integer>(2, 2, 8) {
            private AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Integer getResult() {
                return counter.get();
            }

            @Override
            protected void performExecute() throws TaskException {
                counter.incrementAndGet();
            }

            @Override
            public boolean isDone() {
                return counter.get()==5;
            }

        };

        task.execute();

    }

    @Test(expected=TaskException.class)
    public void testScheduledTaskExecutionWithError() throws InterruptedException, TaskException {

        final ScheduledPoolingTask<Integer> task = new ScheduledPoolingTask<Integer>(2, 2, 8) {
            private AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Integer getResult() {
                return counter.get();
            }

            @Override
            protected void performExecute() throws TaskException {
                throw new TaskException("Throwing an error");
            }

            @Override
            public boolean isDone() {
                return counter.get()==5;
            }

        };

        task.execute();

    }

}
