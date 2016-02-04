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

import java.util.List;

/**
 * Created by serna on 12/9/15.
 */
public class TaskDependingOnOtherTasksExample extends TaskDependingOnOtherTasks<Integer> {

    private int executionTime;
    private int executionCounter = 0;
    private boolean exception;

    private TaskDependingOnOtherTasksExample(Builder builder) {
        super(builder);
        this.executionTime = builder.executionTime;
        this.exception = builder.exception;
    }

    @Override
    protected void performExecute() throws TaskException {
        if (exception){
            throw new TaskException("Exception in main task");
        }
        System.out.println("Executing main task, thread "+Thread.currentThread().getName());
        try {
            Thread.sleep(executionTime);
        } catch (InterruptedException e) {
            executionCounter++;
            e.printStackTrace();
        }
        System.out.println("Main task finished, thread "+Thread.currentThread().getName());
    }

    @Override
    public boolean isDone() {
        return executionCounter==0;
    }

    @Override
    public Integer getResult() {
        return executionCounter;
    }


    public static class Builder extends AbstractBuilder<Builder, TaskDependingOnOtherTasksExample> {

        private int executionTime;
        private boolean exception;

        public Builder willTake(int executionTime){
            this.executionTime = executionTime;
            return this;
        }

        public Builder withException(boolean exception){
            this.exception = exception;
            return this;
        }

        @Override
        public TaskDependingOnOtherTasksExample build() {
            return new TaskDependingOnOtherTasksExample(this);
        }

    }
}
