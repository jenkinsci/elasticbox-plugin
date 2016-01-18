package com.elasticbox.jenkins.model.services.task;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by serna on 12/6/15.
 */
public abstract class TaskDependingOnOtherTasks<R> extends AbstractTask<R> {

    public static final String LINKED_TASK_THREAD_NAME = "LinkedTask";

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

    public List<Task<?>> getLinkedTasks() {
        return dependingOnTasks;
    }

    public Long getTimeout() {
        return timeout;
    }

    @Override
    public void execute() throws TaskException {

        final CountDownLatch countDownLatch = new CountDownLatch(dependingOnTasks.size());

        try {
            performExecute();

            for (final Task task : dependingOnTasks) {
                executorService.submit(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    new EnableTaskWaitForThisToFinishDecorator(task, countDownLatch).execute();
                                } catch (TaskException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                );
            }

            if (!countDownLatch.await(timeout, TimeUnit.SECONDS)){
                //TODO logger timeout reached
                System.out.println("Timeout reached before checking if main task is completed");
                return;
            }

            for (Task<?> task : dependingOnTasks) {
                if (!task.isDone())
                    return;
            }

            checked = true;

        } catch (TaskException e) {
            //TODO logger error doing the main task
            e.printStackTrace();
            throw e;
        } catch (InterruptedException e) {
            //TODO logger
            e.printStackTrace();
            throw new TaskException("Thread interrupted before completion");
        }finally {
            executorService.shutdownNow();
        }

    }

    public boolean isChecked() {
        return checked;
    }

    protected boolean areTasksToCheckDone(){
        for (Task<?> task : dependingOnTasks) {
            if (!task.isDone())
                return false;
        }
        return true;
    }

    protected List<Task<?>> getFailures(){
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

        protected Task taskToExecute;
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