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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by serna on 12/9/15.
 */
public class TestComplexTaskBase {

    protected ScheduledPoolingTask<Integer> createFakeScheduledPoolingTask(long delay, long initialDelay, long timeout, final int succesfullyIteration, final long takeTime){

        return new ScheduledPoolingTask<Integer>(delay, initialDelay, timeout) {
            private AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Integer getResult() {
                if(isDone()){
                    return 0;
                }
                return 1;
            }

            @Override
            void performExecute() throws TaskException {
                try {
                    Thread.sleep(takeTime);
                    counter.incrementAndGet();
                    System.out.println("Executed scheduled task: "+counter+", thread "+Thread.currentThread().getName());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public boolean isDone() {
                return counter.get()==succesfullyIteration;
            }

        };
    }

    protected Task<Integer> createFakeSimpleTask(final long takeTime){
        return new Task<Integer>() {

            private boolean done = false;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public void execute() throws TaskException {
                try {
                    Thread.sleep(takeTime*1000);
                    done = true;
                    System.out.println("Executed task, thread "+Thread.currentThread().getName());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public Integer getResult() {
                if(isDone()){
                    return 0;
                }
                return 1;
            }

        };
    }

    protected Task<Integer> createFakeSimpleTaskThrowingExceptionDuringExecution(){
        return new Task<Integer>() {

            private boolean done = false;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public void execute() throws TaskException {
                throw new TaskException("Error during execution");
            }

            @Override
            public Integer getResult() {
                return 1;
            }

        };
    }
}
