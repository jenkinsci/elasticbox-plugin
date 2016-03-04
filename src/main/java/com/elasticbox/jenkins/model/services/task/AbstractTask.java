package com.elasticbox.jenkins.model.services.task;

public abstract class AbstractTask<R> implements Task<R> {

    protected R result = null;

    protected abstract void performExecute() throws TaskException;

    public R getResult() {
        return result;
    }


}
