/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;

import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ElasticBoxExecutor extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxExecutor.class.getName());

    private static final long RECURRENT_PERIOD =
            Long.getLong("elasticbox.jenkins.ElasticBoxExecutor.recurrentPeriod", 20 * 1000);

    public static final ExecutorService threadPool =
            Executors.newCachedThreadPool(new ExceptionCatchingThreadFactory(new DaemonThreadFactory()));

    public ElasticBoxExecutor() {
        super(ElasticBoxExecutor.class.getName());
    }

    private void executeAsync(final Workload workload, final TaskListener listener) {
        threadPool.submit(new Runnable() {
            public void run() {
                try {
                    workload.execute(listener);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        });
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        List<Workload> syncWorkloads = new ArrayList<Workload>();
        for (Workload workload : Jenkins.get().getExtensionList(Workload.class)) {
            if (workload.getExecutionType() == ExecutionType.ASYNC) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("Executing asynchronous workload: " + workload);
                }
                executeAsync(workload, listener);
            } else {
                syncWorkloads.add(workload);
            }
        }

        for (Workload workload : syncWorkloads) {
            try {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("Executing synchronous workload: " + workload);
                }
                workload.execute(listener);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    @Override
    public Level getNormalLoggingLevel() {
        return Level.FINEST;
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENT_PERIOD;
    }

    public enum ExecutionType {
        SYNC,
        SYNC_WORKLOAD,
        ASYNC
    }

    public abstract static class Workload implements ExtensionPoint {
        private Logger logger = Logger.getLogger(getClass().getName());

        protected abstract ExecutionType getExecutionType();

        protected abstract void execute(TaskListener listener) throws IOException;


        protected void log(Level level, String message) {
            logger.log(level, message);
        }

        protected void log(Level level, String message, Throwable exception) {
            logger.log(level, message, exception);
        }

        protected void log(String message, TaskListener listener) {
            this.log(Level.INFO, message, null, listener);
        }

        protected void log(Level level, String message, Throwable exception, TaskListener listener) {
            listener.getLogger().println(message);
            logger.log(level, message, exception);
        }

    }
}
