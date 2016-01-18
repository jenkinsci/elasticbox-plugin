package com.elasticbox.jenkins.model.services.task;

/**
 * Created by serna on 12/6/15.
 */
public interface Task<R> {

    boolean isDone();

    void execute() throws TaskException;

    R getResult();

}
