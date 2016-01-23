package com.elasticbox.jenkins.model.services.task;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 12/6/15.
 */
public abstract class TaskDependingOnOtherTasks<R> extends AbstractTask<R> {

    private static final Logger logger = Logger.getLogger(TaskDependingOnOtherTasks.class.getName());

    public static final String LINKED_TASK_THREAD_NAME = "DependingOnTaskThread";

    private List<Task<?>> dependingOnTasks;

    private Long timeout;

    private ExecutorService executorService;

    private boolean checked = false;

    protected TaskDependingOnOtherTasks(AbstractBuilder<?, ?> builder){
        this(builder, Executors.newFixedThreadPool(builder.dependingOnTasks.size(),
                new ThreadFactoryBuilder().setNameFormat(LINKED_TASK_THREAD_NAME + " -%d").build()));
    }

    protected TaskDependingOnOtherTasks(AbstractBuilder<?, ?> builder, ExecutorService executor){
        this.dependingOnTasks = builder.dependingOnTasks;
        this.timeout = builder.timeout;
        this.executorService = executor;
    }

    protected abstract boolean prepareDependingOnTasks(R mainTaskResult, List<Task<?>> dependingOnTasks);

    @Override
    public void execute() throws TaskException {

        final CountDownLatch countDownLatch = new CountDownLatch(dependingOnTasks.size());

        try {

            performExecute();

            if(prepareDependingOnTasks(result, dependingOnTasks)){
                for (final Task task : dependingOnTasks) {
                    executorService.submit(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        new EnableTaskWaitForThisToFinishDecorator(task, countDownLatch).execute();
                                    } catch (TaskException e) {
                                        logger.log(Level.SEVERE, "Error executing dependingOnTask: "+task.getClass().getSimpleName(),e);
                                        countDownLatch.countDown();
                                    }}});
                }

                if (!countDownLatch.await(timeout, TimeUnit.SECONDS)){
                    logger.log(Level.SEVERE, "Error, timeout reached executing: "+this.getClass().getSimpleName());
                    throw new TaskException("Error, timeout reached executing: "+this.getClass().getSimpleName());
                }

                logger.log(Level.INFO, "Task "+this.getClass().getSimpleName()+" finished");
            }

        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Thread interrupted waiting for dependingOnTasks to finish",e);
            throw new TaskException("Thread interrupted before completion");
        }finally {
            executorService.shutdownNow();
        }

    }

    public Long getTimeout() {
        return timeout;
    }

    protected List<Task<?>> getDependingOnTasks() {
        return dependingOnTasks;
    }

    protected boolean allDependingOnTasksDone(){
        for (Task<?> task : dependingOnTasks) {
            if (!task.isDone())
                return false;
        }
        return true;
    }

    protected List<Task<?>> getDependingOnTasksFailures(){
        List failures =  new ArrayList();
        for (Task<?> task : dependingOnTasks) {
            if (!task.isDone())
                failures.add(task);
        }
        return failures;
    }

    public interface Builder<T> {
        T build();
    }

    public static abstract class AbstractBuilder<B extends AbstractBuilder<B,T>,T> implements Builder<T> {

        protected List<Task<?>> dependingOnTasks =  new ArrayList<>();
        protected Long timeout;

        public B withDependingTask(Task<?> taskToCheckIfDone){
            this.dependingOnTasks.add(taskToCheckIfDone);
            return getThis();
        }

        public B withTimeout(long timeout){
            this.timeout =  new Long(timeout);
            return getThis();
        }

        @SuppressWarnings("unchecked")
        protected B getThis() {
            return (B) this;
        }

    }


}