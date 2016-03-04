package com.elasticbox.jenkins.model.services.task;

import java.util.concurrent.CountDownLatch;

public class EnableTaskWaitForThisToFinishDecorator<R> implements Task<R> {

    private Task<R> taskToExecute;
    private CountDownLatch countDownLatch;

    public EnableTaskWaitForThisToFinishDecorator(Task<R> task, CountDownLatch countDownLatch) {
        this.taskToExecute = task;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void execute() throws TaskException {

        taskToExecute.execute();

        if (taskToExecute.isDone()) {
            countDownLatch.countDown();
        }
    }

    @Override
    public R getResult() {
        return taskToExecute.getResult();
    }

    @Override
    public boolean isDone() {
        return taskToExecute.isDone();
    }
}
