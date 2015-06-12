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

package com.elasticbox;

import java.io.IOException;
import java.text.MessageFormat;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class AbstractProgressMonitor implements IProgressMonitor {
    private final String resourceUrl;
    private final long creationTime;

    protected AbstractProgressMonitor(String resourceUrl) {
        this.resourceUrl = resourceUrl;
        creationTime = System.currentTimeMillis();
    }

    public String getResourceUrl() {
        return resourceUrl;
    }

    public long getCreationTime() {
        return this.creationTime;
    }


    protected abstract JSONObject getResource() throws IOException;

    protected String getState(JSONObject resource) {
        return resource.getString("state");
    }

    public boolean isDone() throws IProgressMonitor.IncompleteException, IOException {
        return isDone(getResource());
    }

    public void waitForDone(int timeout) throws IProgressMonitor.IncompleteException, IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        long remainingTime = timeout * 60000;
        do {
            if (isDone()) {
                return;
            }

            synchronized(this) {
                wait(1000);
            }

            long currentTime = System.currentTimeMillis();
            remainingTime =  remainingTime - (currentTime - startTime);
            startTime = currentTime;
        } while (timeout <= 0 || remainingTime > 0);

        JSONObject resource = getResource();
        if (!isDone(resource)) {
            throw new IProgressMonitor.TimeoutException(
                    MessageFormat.format("{0} is not in ready after waiting for {1} minutes. Current state: {2}",
                            getResourceUrl(), timeout, getState(resource)));

        }
    }

}
