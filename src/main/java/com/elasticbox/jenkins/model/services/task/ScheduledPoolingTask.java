package com.elasticbox.jenkins.model.services.task;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;

/**
 * Created by serna on 12/7/15.
 */
public abstract class ScheduledPoolingTask<R> extends AbstractTask<R> {

    private long delay;
    private long initialDelay;
    private long timeout;

    private ScheduledExecutorService scheduledExecutorService = null;
    private ScheduledFuture<?> scheduledFuture = null;


    public ScheduledPoolingTask(long delay, long initialDelay, long timeout) {

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("ScheduledPoolingTask-%d")
                .build();

        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);

        this.delay = delay;
        this.initialDelay =  initialDelay;
        this.timeout = timeout;
    }


    @Override
    public void execute() throws TaskException {

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            performExecute();

                            if (isDone()) {
                                scheduledFuture.cancel(true);
                                countDownLatch.countDown();
                            }

                        } catch (TaskException e) {
                            //TODO logger
                            e.printStackTrace();
                        }
                    }
                }, initialDelay, delay, TimeUnit.SECONDS);

        try {
            final boolean await = countDownLatch.await(timeout, TimeUnit.SECONDS);
            if (!await){
                //TODO logger
                throw new TaskException("Timeout reached");
            }

        } catch (InterruptedException e) {
            //TODO logger
            e.printStackTrace();
            throw new TaskException("Thread interrupted before completion");
        }finally {
            scheduledExecutorService.shutdownNow();
        }

    }

}
