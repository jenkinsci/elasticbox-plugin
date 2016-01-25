package com.elasticbox.jenkins.model.services.task;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 12/7/15.
 */
public abstract class ScheduledPoolingTask<R> extends AbstractTask<R> {

    private static final Logger logger = Logger.getLogger(ScheduledPoolingTask.class.getName());

    protected int counter = 0;
    private long delay;
    private long initialDelay;
    private long timeout;

    private ScheduledExecutorService scheduledExecutorService = null;
    private ScheduledFuture<?> scheduledFuture = null;


    public ScheduledPoolingTask(long delay, long initialDelay, long timeout) {

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("ScheduledPoolingTask["+this.getClass().getSimpleName()+"]-%d")
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

                            counter++;

                            if (isDone()) {
                                scheduledFuture.cancel(true);
                                countDownLatch.countDown();
                            } else {
                                result = null;
                            }

                        } catch (TaskException e) {
                            result = null;
                            logger.log(Level.SEVERE, "Error executing task: "+this.getClass().getSimpleName(),e);
                            scheduledFuture.cancel(true);
                            countDownLatch.countDown();
                        }
                    }
                }, initialDelay, delay, TimeUnit.SECONDS);

        try {
            final boolean await = countDownLatch.await(timeout, TimeUnit.SECONDS);
            if (!await){
                logger.log(Level.SEVERE, "Timeout reached("+timeout+" secs) executing task: "+this.getClass().getSimpleName());
                throw new TaskException("Timeout reached("+timeout+" secs) executing task: "+this.getClass().getSimpleName());
            }else{
                logger.log(Level.INFO, "ScheduledPoolingTask: "+this.getClass().getSimpleName()+" finished");
            }

        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Thread interrupted before completion executing task: "+this.getClass().getSimpleName(),e);
            throw new TaskException("Thread interrupted before completion executing task: "+this.getClass().getSimpleName());
        }finally {
            scheduledExecutorService.shutdownNow();
        }

    }

    public int getCounter() {
        return counter;
    }
}
