package com.elasticbox.jenkins.model.services.task;

public interface Task<R> {

    boolean isDone();

    void execute() throws TaskException;

    R getResult();

}
