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

package com.elasticbox.jenkins.tests;

import com.elasticbox.jenkins.util.Condition;
import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class TestBase {
    private static final Logger LOGGER = Logger.getLogger(TestBase.class.getName());

    protected final List<JSONObject> objectsToDelete = new ArrayList<JSONObject>();
    protected ElasticBoxCloud cloud;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setup() throws Exception {
        LOGGER.info("Test timeout: " + jenkinsRule.timeout);
        cloud = new ElasticBoxCloud("elasticbox", "ElasticBox", TestUtils.ELASTICBOX_URL, 2, TestUtils.ACCESS_TOKEN, Collections.EMPTY_LIST);
        LOGGER.fine("Elasticbox cloud: " + cloud);
        jenkinsRule.getInstance().clouds.add(cloud);
    }

    @After
    public void tearDown() throws Exception {
        LOGGER.fine("tearDown cloud: " + cloud);
        final Client client = cloud.getClient();

        // terminate and delete instances
        final Map<String, IProgressMonitor> terminatingInstancIdToMonitorMap = new HashMap<String, IProgressMonitor>();
        for (Iterator<JSONObject> iter = objectsToDelete.iterator(); iter.hasNext();) {
            JSONObject object = iter.next();
            String uri = object.getString("uri");
            if (uri != null && uri.startsWith("/services/instances/")) {
                String instanceId = null;
                try {
                    instanceId = object.getString("id");
                    IProgressMonitor monitor = client.forceTerminate(instanceId);
                    terminatingInstancIdToMonitorMap.put(instanceId, monitor);
                    iter.remove();
                } catch (IOException ex) {
                    boolean isAlreadyDeleted = false;
                    if (ex instanceof ClientException) {
                        int exStatusCode = ((ClientException) ex).getStatusCode();
                        // SC_NOT_FOUND admitted for compatibility with previous versions to CAM 5.0.22033
                        isAlreadyDeleted = (exStatusCode == HttpStatus.SC_FORBIDDEN) || (exStatusCode == HttpStatus.SC_NOT_FOUND);
                    }
                    if(isAlreadyDeleted){
                        LOGGER.log(Level.INFO, "Instance \"" + instanceId + "\" was already deleted.");
                    } else {
                        LOGGER.log(Level.WARNING, ex.getMessage(), ex);
                    }
                }
            }
        }
        List<String> instanceIDs = new ArrayList<String>(terminatingInstancIdToMonitorMap.keySet());
        new Condition() {

            public boolean satisfied() {
                JSONArray instances;
                try {
                    instances = client.getInstances(new ArrayList<String>(terminatingInstancIdToMonitorMap.keySet()));
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    return false;
                }
                Set<String> instanceIDs = new HashSet<String>();
                for (Object instance : instances) {
                    JSONObject instanceJson = (JSONObject) instance;
                    String instanceId = instanceJson.getString("id");
                    instanceIDs.add(instanceId);
                    IProgressMonitor monitor = terminatingInstancIdToMonitorMap.get(instanceId);
                    boolean done = false;
                    try {
                        done = monitor.isDone(instanceJson);
                    } catch (IProgressMonitor.IncompleteException ex) {
                        done = true;
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                    if (done) {
                        terminatingInstancIdToMonitorMap.remove(instanceId);
                    }
                }
                Set<String> notFoundInstanceIDs = new HashSet<String>(terminatingInstancIdToMonitorMap.keySet());
                notFoundInstanceIDs.removeAll(instanceIDs);
                terminatingInstancIdToMonitorMap.keySet().removeAll(notFoundInstanceIDs);
                return terminatingInstancIdToMonitorMap.isEmpty();
            }

        }.waitUntilSatisfied(360, "terminatingInstancIdToMonitorMap");
        for (String instanceId : instanceIDs) {
            try {
                client.delete(instanceId);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, ex.getMessage(), ex);
            }
        }

        for (int i = objectsToDelete.size() - 1; i > -1; i--) {
            try {
                delete(objectsToDelete.get(i), client);
            } catch (IOException ex) {
                boolean isAlreadyDeleted = false;
                if (ex instanceof ClientException) {
                    int exStatusCode = ((ClientException) ex).getStatusCode();
                    // SC_NOT_FOUND admitted for compatibility with previous versions
                    isAlreadyDeleted = (exStatusCode == HttpStatus.SC_FORBIDDEN) || (exStatusCode == HttpStatus.SC_NOT_FOUND);
                }
                if(isAlreadyDeleted){
                    String instanceId = objectsToDelete.get(i).getString("id");
                    LOGGER.log(Level.INFO, "Instance \"" + instanceId + "\" pending to delete was already deleted.");
                } else {
                    LOGGER.log(Level.WARNING, ex.getMessage(), ex);
                }
            }
        }
    }

    protected boolean deleteAfter(JSONObject object) {
        if (object.containsKey("uri")) {
            String uri = object.getString("uri");
            for (JSONObject json : objectsToDelete) {
                if (json.getString("uri").equals(uri)) {
                    return false;
                }
            }
            objectsToDelete.add(object);
            return true;
        } else {
            return false;
        }
    }

    protected void delete(JSONObject resource, Client client) throws IOException {
        if (resource.getString("schema").endsWith("/provider")) {
            // delete all policy boxes of the provider
            String providerId = resource.getString("id");
            for (Object policy : client.getProfiles(resource.getString("owner"))) {
                JSONObject policyJson = (JSONObject) policy;
                if (policyJson.getString("provider_id").equals(providerId)) {
                    client.doDelete(policyJson.getString("uri"));
                }
            }
        }
        client.doDelete(resource.getString("uri"));
    }

    public void setLoggerLevel(String name, Level level) {
        jenkinsRule.getInstance().getLog().doConfigLogger(name, level.toString() );
        final Logger logger = Logger.getLogger(name);
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false);
    }
}
