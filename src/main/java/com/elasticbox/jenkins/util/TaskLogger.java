/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.util;

import hudson.model.TaskListener;
import java.text.MessageFormat;

/**
 *
 * @author Phong Nguyen Le
 */
public class TaskLogger {
    private static final String PREFIX = "[ElasticBox] - ";
    private final TaskListener taskListener;

    public TaskLogger(TaskListener listener) {
        taskListener = listener;
    }

    public TaskListener getTaskListener() {
        return taskListener;
    }

    public void info(String msg) {
        taskListener.getLogger().println(PREFIX + msg);
    }

    public void info(String format, Object... args) {
        taskListener.getLogger().println(PREFIX + MessageFormat.format(format, args));
    }

    public void error(String msg) {
        taskListener.error(PREFIX + msg);
    }

    public void error(String format, Object... args) {
        taskListener.error(PREFIX + MessageFormat.format(format, args));
    }

}
