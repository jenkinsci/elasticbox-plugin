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

package com.elasticbox.jenkins;

import com.elasticbox.IProgressMonitor;

import hudson.slaves.SlaveComputer;

import net.sf.json.JSONObject;

import org.apache.commons.lang.time.StopWatch;

import java.io.IOException;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

class LaunchSlaveProgressMonitor implements IProgressMonitor {

    private static final Logger LOGGER = Logger.getLogger(LaunchSlaveProgressMonitor.class.getName());

    private final Object waitLock = new Object();
    private final long creationTime;
    private final ElasticBoxSlave slave;
    private IProgressMonitor monitor;
    private long launchTime;

    public LaunchSlaveProgressMonitor(ElasticBoxSlave slave) {
        creationTime = System.currentTimeMillis();
        this.slave = slave;
    }

    @Override
    public String getResourceUrl() {
        return monitor != null ? monitor.getResourceUrl() : null;
    }

    @Override
    public boolean isDone() throws IncompleteException, IOException {
        return monitor != null ? monitor.isDone() : false;
    }

    @Override
    public boolean isDone(JSONObject instance) throws IncompleteException, IOException {
        return monitor != null ? monitor.isDone(instance) : false;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    void setMonitor(IProgressMonitor monitor) {
        this.monitor = monitor;
        synchronized (waitLock) {
            waitLock.notifyAll();
        }
    }

    private void wait(Callable<Boolean> condition, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        long remainingTime = timeout;
        while ((timeout <= 0 || remainingTime > 0) && condition.call()) {
            synchronized (waitLock) {
                try {
                    waitLock.wait(remainingTime);
                } catch (InterruptedException ex) {
                    LOGGER.log(
                        Level.SEVERE,
                        "Thread interrupted waiting for condition",
                        ex
                    );
                }
            }
            long currentTime = System.currentTimeMillis();
            remainingTime = remainingTime - (currentTime - startTime);
            startTime = currentTime;
        }
    }

    @Override
    public void waitForDone(int timeout) throws IncompleteException, IOException, InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        long timeoutMiliseconds = timeout * 60000;
        long remainingTime = timeoutMiliseconds;
        try {
            wait(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return monitor == null;
                }
            }, timeoutMiliseconds);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
        if (monitor == IProgressMonitor.DONE_MONITOR) {
            return;
        }
        remainingTime = remainingTime - stopWatch.getTime();
        if (monitor != null && remainingTime > 0) {
            monitor.waitForDone((int) Math.ceil(remainingTime / 60000));
        }
        remainingTime = remainingTime - stopWatch.getTime();
        if (remainingTime > 0) {
            try {
                wait(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        SlaveComputer computer = slave.getComputer();
                        return computer != null && computer.isOffline();
                    }
                }, remainingTime);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    void setLaunched() {
        launchTime = System.currentTimeMillis();
    }

    public long getLaunchTime() {
        return launchTime;
    }

}
