package com.elasticbox.jenkins.services.error;

/**
 * Created by serna on 11/30/15.
 */
public class ServiceException extends Throwable {
    public ServiceException(String message) {
        super(message);
    }
}
