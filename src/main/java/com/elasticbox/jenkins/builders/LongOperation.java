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

package com.elasticbox.jenkins.builders;

import com.elasticbox.Client;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.util.TaskLogger;

import hudson.AbortException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class LongOperation extends Operation {

    private final boolean waitForCompletion;
    private int waitForCompletionTimeout;

    protected LongOperation(String tags, boolean waitForCompletion, int waitForCompletionTimeout) {
        super(tags);
        this.waitForCompletion = waitForCompletion;
        this.waitForCompletionTimeout = waitForCompletionTimeout;
    }

    protected Object readResolve() {
        if (waitForCompletion && waitForCompletionTimeout == 0) {
            waitForCompletionTimeout = 60;
        }
        return this;
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    public int getWaitForCompletionTimeout() {
        return waitForCompletionTimeout;
    }

    static void waitForCompletion(String operationDisplayName, List<IProgressMonitor> monitors, Client client,
            TaskLogger logger, int timeoutMinutes) throws IOException, InterruptedException {
        Map<String, IProgressMonitor> instanceIdToMonitorMap = new HashMap<String, IProgressMonitor>();
        for (IProgressMonitor monitor : monitors) {
            instanceIdToMonitorMap.put(Client.getResourceId(monitor.getResourceUrl()), monitor);
        }
        Object waitLock = new Object();
        long startWaitTime = System.currentTimeMillis();

        while (!instanceIdToMonitorMap.isEmpty()
            && TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startWaitTime) < timeoutMinutes) {

            synchronized (waitLock) {
                waitLock.wait(3000);
            }
            List<String> instanceIDs = new ArrayList<String>(instanceIdToMonitorMap.keySet());
            JSONArray instances = client.getInstances(instanceIDs);
            for (Object instance : instances) {
                JSONObject instanceJson = (JSONObject) instance;
                String instanceId = instanceJson.getString("id");
                instanceIDs.remove(instanceId);
                IProgressMonitor monitor = instanceIdToMonitorMap.get(instanceId);
                String instancePageUrl = Client.getPageUrl(client.getEndpointUrl(), instanceJson);
                boolean done;
                try {
                    done = monitor.isDone(instanceJson);
                } catch (IProgressMonitor.IncompleteException ex) {
                    logger.error("Failed to perform operation {0} for instance {1}: {1}", operationDisplayName,
                            instancePageUrl, ex.getMessage());
                    throw new AbortException(ex.getMessage());
                }
                if (done) {
                    logger.info(MessageFormat.format("Operation {0} is successful for instance {1}",
                            operationDisplayName, instancePageUrl));
                    instanceIdToMonitorMap.remove(instanceId);
                }
            }
            if (!instanceIDs.isEmpty()) {
                throw new AbortException(MessageFormat.format("Cannot find the instances with the following IDs: {0}",
                        StringUtils.join(instanceIDs, ", ")));
            }
        }
        
        if (!instanceIdToMonitorMap.isEmpty()) {

            List<String> instancePageUrls = new ArrayList<String>();

            for (IProgressMonitor monitor : instanceIdToMonitorMap.values()) {
                instancePageUrls.add(Client.getPageUrl(client.getEndpointUrl(), monitor.getResourceUrl()));
            }

            String message = MessageFormat.format(
                "The following instances still are not ready after waiting for {0} minutes: {1}",
                TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startWaitTime),
                StringUtils.join(instancePageUrls, ','));

            logger.error(message);

            throw new AbortException(message);
        }
    }

}
