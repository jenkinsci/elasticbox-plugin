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

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.TextParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.Scrambler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;

/**
 *
 * @author Phong Nguyen Le
 */
public class TestUtils {
    static final String ELASTICBOX_URL = System.getProperty("elasticbox.jenkins.test.ElasticBoxURL", "https://blue.elasticbox.com");
    static final String OPS_PASSWORD_PROPERTY = "elasticbox.jenkins.test.opsPassword";
    static final String OPS_USER_NAME_PROPERTY = "elasticbox.jenkins.test.opsUsername";
    static final String TEST_WORKSPACE = System.getProperty("elasticbox.jenkins.test.workspace", "tphongio");
    
    static final String TEST_LINUX_BOX_NAME = "test-linux-box";    
    static final String TEST_NESTED_BOX_NAME = "test-nested-box";    
    static final String TEST_BINDING_BOX_NAME = "test-binding-box";
    static final String TEST_BINDING_BOX_INSTANCE_ID = "i-c51bop";
    
    static final String JENKINS_SLAVE_BOX_NAME = "test-linux-jenkins-slave";
    static final String PASSWORD = System.getProperty("elasticbox.jenkins.test.password", Scrambler.descramble("dHBob25naW8="));
    static final String USER_NAME = System.getProperty("elasticbox.jenkins.test.username", Scrambler.descramble("dHBob25naW9AZ21haWwuY29t"));
    static final String JENKINS_PUBLIC_HOST = System.getProperty("elasticbox.jenkins.test.jenkinsPublicHost", "localhost");

    static JSONObject findVariable(JSONArray variables, String name, String scope) {
        for (Object variable : variables) {
            JSONObject variableJson = (JSONObject) variable;
            if (variableJson.getString("name").equals(name)) {
                if (scope == null) {
                    scope = StringUtils.EMPTY;
                }
                String variableScope = variableJson.containsKey("scope") ? variableJson.getString("scope") : StringUtils.EMPTY;
                if (scope.equals(variableScope)) {
                    return variableJson;
                }
            }
        }
        return null;
    }

    static JSONObject findVariable(JSONArray variables, String name) {
        return findVariable(variables, name, null);
    }
    
    public static JSONObject findInstance(JSONArray instances, String... tags) {
        Collection<String> tagList = Arrays.asList(tags);
        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;
            if (instanceJson.getJSONArray("tags").containsAll(tagList)) {
                return instanceJson;
            }
        }        
        return null;
    }
    
    static String getResourceAsString(String resourcePath) throws IOException {
        return IOUtils.toString((InputStream) TestUtils.class.getResource(resourcePath).getContent());
    }

    static FreeStyleBuild runJob(String name, String projectXml, Map<String, String> textParameters, Jenkins jenkins) throws Exception {
        FreeStyleProject project = (FreeStyleProject) jenkins.createProjectFromXML(name, new ByteArrayInputStream(projectXml.getBytes()));
        List<ParameterValue> parameters = new ArrayList<ParameterValue>();
        for (Map.Entry<String, String> entry : textParameters.entrySet()) {
            parameters.add(new TextParameterValue(entry.getKey(), entry.getValue()));
        }
        QueueTaskFuture future = project.scheduleBuild2(0, new Cause.LegacyCodeCause(), new ParametersAction(parameters));
        Future startCondition = future.getStartCondition();
        startCondition.get(60, TimeUnit.MINUTES);
        return (FreeStyleBuild) future.get(60, TimeUnit.MINUTES);
    }
    
    static void cleanUp(String testTag, Jenkins jenkins) throws Exception {
        FreeStyleBuild build = TestUtils.runJob("cleanup", getResourceAsString("CleanupJob.xml"), 
                Collections.singletonMap("TEST_TAG", testTag), jenkins);
        TestUtils.assertBuildSuccess(build);                
    }

    static void assertBuildSuccess(FreeStyleBuild build) throws IOException {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        build.getLogText().writeLogTo(0, log);
        Assert.assertEquals(log.toString(), Result.SUCCESS, build.getResult());
    }

    static Object getResult(Future<?> future, int waitMinutes) throws ExecutionException {
        Object result = null;
        long maxWaitTime = waitMinutes * 60000;
        long waitTime = 0;
        do {
            long waitStart = System.currentTimeMillis();
            try {
                result = future.get(maxWaitTime - waitTime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
            } catch (TimeoutException ex) {
                break;
            }
            waitTime += (System.currentTimeMillis() - waitStart);
        } while (result == null && waitTime < maxWaitTime);
        return result;
    }

}
