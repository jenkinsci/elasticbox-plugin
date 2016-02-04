package com.elasticbox.jenkins.model.services.task;

/**
 * Created by serna on 12/7/15.
 */
public class TaskException extends Exception{
    public TaskException(String message) {
        super(message);
    }

    public TaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
