/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.services.instances.execution.task;

import com.elasticbox.Client;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.InstanceRepositoryAPIImpl;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.task.ScheduledPoolingTask;
import com.elasticbox.jenkins.model.services.task.Task;
import com.elasticbox.jenkins.model.services.task.TaskException;
import com.elasticbox.jenkins.util.TaskLogger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 2/8/16.
 */
public class UpdateInstanceTask implements Task<JSONObject> {

    private static final Logger logger = Logger.getLogger(UpdateInstanceTask.class.getName());


    private static final long DELAY = 5;
    private static final long INITIAL_DELAY = 2;
    private static final long TIMEOUT = 3600;

    private static final int READING_STATE_RETRY_COUNT = 24;
    private static final int RETRY_COUNT = 2;

    private long delay;
    private long initialDelay;
    private long timeout;

    private boolean waitForInstanceToBeReady;

    private int retries;
    private int readingStateRetries;


    private Client client;
    private JSONObject instanceJson;
    private JSONArray resolvedVariables;
    private String boxVersion;
    private TaskLogger taskLogger;

    private JSONObject updatedJSONInstance;
    private int totalRoundtrips = 0;


    public UpdateInstanceTask(Client client, TaskLogger logger, JSONObject instanceJson, JSONArray resolvedVariables, String boxVersion) {
        this(client, logger, instanceJson, resolvedVariables, boxVersion, true, INITIAL_DELAY, DELAY, TIMEOUT, READING_STATE_RETRY_COUNT, RETRY_COUNT);
    }

    public UpdateInstanceTask(Client client,
                              TaskLogger logger,
                              JSONObject instanceJson,
                              JSONArray resolvedVariables,
                              String boxVersion,
                              boolean waitForInstanceToBeReady,
                              long initialDelay,
                              long delay,
                              long timeout,
                              int readingStateRetries,
                              int retries) {

        this.waitForInstanceToBeReady = waitForInstanceToBeReady;
        this.client = client;
        this.instanceJson = instanceJson;
        this.resolvedVariables = resolvedVariables;
        this.boxVersion = boxVersion;
        this.delay = delay;
        this.initialDelay = initialDelay;
        this.timeout = timeout;
        this.taskLogger = logger;
        this.readingStateRetries = readingStateRetries;
        this.retries = retries;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override

    public void execute() throws TaskException {

        for (int i = 0; i < retries; i++) {
            if(updateInstance()){
                return;
            }
            logger.log(Level.WARNING, "Retry instance: "+instanceJson.getString("id")+" updating action");
        }

        throw new TaskException("Instance: "+ instanceJson.getString("id")+" cannot be updated");
    }

    private boolean updateInstance(){

        final String instanceId = instanceJson.getString("id");
        int readingExecutions = 0;
        try {
            if (waitForInstanceToBeReady){
                readingExecutions = waitForInstanceToBeReady(instanceId);
            }

            updatedJSONInstance = client.updateInstance(instanceJson, resolvedVariables, boxVersion);
            totalRoundtrips = readingExecutions;

            return  true;

        } catch (RepositoryException | IOException | TaskException ex) {
            logger.log(Level.SEVERE, "Instance: "+instanceId+" cannot be updated", ex);
            taskLogger.info("Instance {0} cannot be updated", instanceId);

            return false;
        }

    }

    private int waitForInstanceToBeReady(String instanceId) throws TaskException, RepositoryException {

        final InstanceRepositoryAPIImpl service = new InstanceRepositoryAPIImpl(client);

        final Instance instance = service.getInstance(instanceId);

        final Instance.State state = instance.getState();

        WaitForInstanceToBeReady updateInstanceWhenIsReady = null;
        if (state == Instance.State.PROCESSING){

            taskLogger.info("Waiting for instance: {0} to be ready for update", instanceId);

            updateInstanceWhenIsReady =  new WaitForInstanceToBeReady(DELAY, INITIAL_DELAY, TIMEOUT);
            updateInstanceWhenIsReady.execute();

            return updateInstanceWhenIsReady.getTotalRoundtrips();
        }

        taskLogger.info("Instance {0} ready to be updated", instanceId);

        return 0;
    }

    @Override
    public JSONObject getResult() {
        return updatedJSONInstance;
    }

    public int getTotalRoundtrips() {
        return totalRoundtrips;
    }

    private class WaitForInstanceToBeReady extends ScheduledPoolingTask<Instance>{

        private AtomicInteger errors = new AtomicInteger(0);

        public WaitForInstanceToBeReady(long delay, long initialDelay, long timeout) {
            super(delay, initialDelay, timeout);
        }

        @Override
        protected void performExecute() throws TaskException {
            final String instanceId = instanceJson.getString("id");
            try {
                this.result = new InstanceRepositoryAPIImpl(client).getInstance(instanceId);
            } catch (RepositoryException e) {

                final int errorCounter = errors.incrementAndGet();

                logger.log(Level.SEVERE, "Error ["+errorCounter+"] getting instance: "+instanceJson.getString("id"),e);
                taskLogger.error("Error {0} checking the state of the instance: {1}", errorCounter, instanceJson.getString("id"));

                if(errorCounter > readingStateRetries){
                    throw new TaskException("Error checking instance: "+instanceJson.getString("id"));
                }
            }
        }

        @Override
        public boolean isDone() {
            if(result != null){
                return this.result.getState() != Instance.State.PROCESSING;
            }
            return  false;
        }

        public int getTotalRoundtrips(){
            return this.getCounter();
        }
    };


}
