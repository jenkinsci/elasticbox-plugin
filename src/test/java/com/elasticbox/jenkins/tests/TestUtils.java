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

import com.elasticbox.Client;
import com.elasticbox.Constants;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import com.elasticbox.jenkins.util.Condition;
import com.hubspot.jinjava.Jinjava;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.TextParameterValue;
import hudson.model.queue.QueueTaskFuture;

import java.io.*;
import java.net.URI;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;

/**
 *
 * @author Phong Nguyen Le
 */
public class TestUtils {
    private static final Logger LOGGER = Logger.getLogger(TestUtils.class.getName());

    static final Jinjava JINJA_RENDER = new Jinjava();
    static final String GITHUB_PUBLIC_ADDRESS = "github.com";
    public static final String ELASTICBOX_URL = System.getProperty("elasticbox.jenkins.test.ElasticBoxURL", "https://blue.elasticbox.com");
    static final String DEFAULT_TEST_WORKSPACE = "test_admin";
    static final String TEST_WORKSPACE = System.getProperty("elasticbox.jenkins.test.workspace", DEFAULT_TEST_WORKSPACE);

    static final String TEST_LINUX_BOX_NAME = "test-linux-box";
    static final String TEST_NESTED_BOX_NAME = "test-nested-box";
    static final String TEST_BINDING_BOX_NAME = "test-binding-box";
    static final String TEST_BINDING_BOX_INSTANCE_ID = "com.elasticbox.jenkins.tests.instances." + TEST_BINDING_BOX_NAME;
    static final String TEST_BINDING_INSTANCE_TAG = "test-instance-binding";

    static final String JENKINS_SLAVE_BOX_NAME = "Linux Jenkins Slave";
    public static final String CLOUD_CREDENTIALS_ID = System.getProperty("elasticbox.jenkins.test.credentialsId", "52625622-3008-41fe-88b4-4fbe64595d2a");
    public static final String ACCESS_TOKEN = System.getProperty("elasticbox.jenkins.test.accessToken", "52625622-3008-41fe-88b4-4fbe64595d2a");
    static final String JENKINS_PUBLIC_HOST = System.getProperty("elasticbox.jenkins.test.jenkinsPublicHost", "localhost");
    static final String TEST_JENKINS_PUBLIC_HOST = "myJenkinsUrl.test";
    static final String TEST_PROVIDER_TYPE = "Test Provider";
    static final String TEST_TAG = System.getProperty("elasticbox.jenkins.test.tag", "jenkins-plugin-test");
    static final String NAME_PREFIX = TEST_TAG + '-';
    static final String LINUX_COMPUTE = "Linux Compute";


    static final Properties GITHUB_PROPERTIES = loadGitHubProperties(System.getProperty("elasticbox.jenkins.test.GitHubProperties"));
    static final String GITHUB_USER = GITHUB_PROPERTIES.getProperty("GITHUB_USER");
    static final String GITHUB_REPO_NAME = GITHUB_PROPERTIES.getProperty("GITHUB_REPO");
    static final String GITHUB_ACCESS_TOKEN = GITHUB_PROPERTIES.getProperty("GITHUB_TOKEN");
    static final String GITHUB_ADDRESS =GITHUB_PROPERTIES.getProperty("GITHUB_ADDRESS");
    static final String GITHUB_TEST_BRANCH = GITHUB_PROPERTIES.getProperty("GITHUB_BRANCH");

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

    private static Properties loadGitHubProperties(String propertiesFilePath) {
        Properties properties = new Properties();

        if (propertiesFilePath == null) {
            LOGGER.log(Level.INFO, "GitHub properties for tests are not specified");
            return properties;
        }

        try {
            FileInputStream input = new FileInputStream(propertiesFilePath);
            properties.load(input);
            input.close();
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} specified file not found. GitHub properties for tests are not loaded", propertiesFilePath));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error loading GitHub properties file {0} due to: {1}", propertiesFilePath, e.getMessage()));
        }

        return properties;
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
        return IOUtils.toString((InputStream) TestUtils.class.getResource(resourcePath).getContent(),  "UTF-8");
    }

    static FreeStyleProject createProject(String name, String projectXml, Jenkins jenkins) throws IOException {
        return (FreeStyleProject) jenkins.createProjectFromXML(name, new ByteArrayInputStream(projectXml.getBytes()));
    }

    static FreeStyleBuild runJob(String name, String projectXml, Map<String, String> textParameters, Jenkins jenkins) throws Exception {
        FreeStyleProject project = (FreeStyleProject) jenkins.createProjectFromXML(name, new ByteArrayInputStream(projectXml.getBytes()));
        return runJob(project, textParameters, jenkins);
    }

    static FreeStyleBuild runJob(FreeStyleProject project, Map<String, String> textParameters, Jenkins jenkins) throws Exception {
        List<ParameterValue> parameters = new ArrayList<ParameterValue>();
        for (Map.Entry<String, String> entry : textParameters.entrySet()) {
            parameters.add(new TextParameterValue(entry.getKey(), entry.getValue()));
        }
        final QueueTaskFuture future = project.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(parameters));
        Future startCondition = future.getStartCondition();
        startCondition.get(60, TimeUnit.MINUTES);
        final FreeStyleBuild[]  buildHolder = new FreeStyleBuild[1];
        new Condition("runJob buildHolder") {

            @Override
            public boolean satisfied() {
                try {
                    buildHolder[0] = (FreeStyleBuild) future.get(60, TimeUnit.MINUTES);
                    return true;
                } catch (InterruptedException ex) {
                    return false;
                } catch (ExecutionException ex) {
                    throw new RuntimeException(ex);
                } catch (TimeoutException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }.waitUntilSatisfied(TimeUnit.MINUTES.toSeconds(ElasticBoxSlaveHandler.TIMEOUT_MINUTES));
        if (buildHolder[0] == null) {
            throw new Exception(MessageFormat.format("Cannot retrieve build after {0} minites",
                    ElasticBoxSlaveHandler.TIMEOUT_MINUTES));
        }

        return buildHolder[0];
    }

    static void cleanUp(String testTag, Jenkins jenkins) throws Exception {
        cleanUp(testTag, TEST_WORKSPACE, jenkins);
    }

    static void cleanUp(String testTag, String workspace, Jenkins jenkins) throws Exception {
        FreeStyleBuild build = TestUtils.runJob(MessageFormat.format("cleanup-{0}", UUID.randomUUID()),
                getResourceAsString("jobs/cleanup.xml").replace(DEFAULT_TEST_WORKSPACE, workspace),
                Collections.singletonMap("TEST_TAG", testTag), jenkins);
        TestUtils.assertBuildSuccess(build);
    }

    static String getLog(AbstractBuild build) throws IOException {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        build.getLogText().writeLogTo(0, log);
        return log.toString();
    }

    static void assertBuildSuccess(AbstractBuild build) throws IOException {
        Assert.assertEquals(getLog(build), Result.SUCCESS, build.getResult());
    }

    static Object getResult(Future<?> future, int waitMinutes) throws ExecutionException {
        Object result = null;
        long maxWaitTime = waitMinutes * 60000;
        long waitTime = 0;

        do {
            long waitStart = System.currentTimeMillis();
            try {
                result = future.get(maxWaitTime - waitTime, TimeUnit.MILLISECONDS);

            } catch (InterruptedException e) {
                if (LOGGER.isLoggable(Level.FINEST) ) {
                    LOGGER.finest("Waiting for the schedule result... ");
                }
            } catch (TimeoutException ex) {
                LOGGER.severe("Timeout reached:" + waitMinutes + " min - " + ex);
                break;
            }
            waitTime += (System.currentTimeMillis() - waitStart);
        } while (result == null && waitTime < maxWaitTime);
        return result;
    }

    static JSONObject createTestProvider(Client client) throws IOException, InterruptedException {
        JSONObject testProvider = new JSONObject();
        testProvider.put("name", NAME_PREFIX + UUID.randomUUID().toString());
        testProvider.put("schema", MessageFormat.format("{0}test/provider", Constants.BASE_ELASTICBOX_SCHEMA));
        testProvider.put("type", TEST_PROVIDER_TYPE);
        testProvider.put("icon", "images/platform/provider.png");
        testProvider.put("secret", "secret");
        testProvider.put("owner", TestUtils.TEST_WORKSPACE);
        final IProgressMonitor monitor = client.createProvider(testProvider);
        new Condition() {

            @Override
            public boolean satisfied() {
                try {
                    return monitor.isDone();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }.waitUntilSatisfied(TimeUnit.MINUTES.toSeconds(10));
        return (JSONObject) client.doGet(monitor.getResourceUrl(), false);
    }

    public static interface TemplateResolver {
        public String resolve(String template);
    }

    public static class MappingTemplateResolver implements TemplateResolver {
        private final Map<String, String> oldValueToNewValueMap = new HashMap<String, String>();

        public String resolve(String template) {
            for (Map.Entry<String, String> entry : oldValueToNewValueMap.entrySet()) {
                template = template.replace(entry.getKey(), entry.getValue());
            }
            return template;
        }

        public void map(String oldValue, String newValue) {
            oldValueToNewValueMap.put(oldValue, newValue);
        }
    }

    private static class TemplateResolverImpl implements TemplateResolver {
        private final TemplateResolver resolver;

        public TemplateResolverImpl(Client client, TemplateResolver resolver) throws IOException {
            this.resolver = resolver;
        }

        public String resolve(String template) {
            if (resolver != null) {
                template = resolver.resolve(template);
            }
            return template;
        }

    }

    static TestBoxData createTestBox(String jsonFilePath, TemplateResolver resolver, Client client) throws Exception {
        TestBoxData testBoxData = new TestBoxData(jsonFilePath, null);
        createTestBox(testBoxData, resolver, client);
        return testBoxData;
    }

    static JSONObject createTestBox(TestBoxData testBoxData, TemplateResolver resolver, Client client) throws Exception {
        JSONObject box = JSONObject.fromObject(loadBox(testBoxData.jsonFileName, new TemplateResolverImpl(client, resolver)));
        box.put("name", box.getString("name") + '-' + UUID.randomUUID().toString());
        box.put("owner", TestUtils.TEST_WORKSPACE);
        JSONArray requirements;
        if (box.containsKey("requirements")) {
            requirements = box.getJSONArray("requirements");
        } else {
            requirements = new JSONArray();
            box.put("requirements", requirements);
        }
        if (!requirements.contains(TEST_TAG)) {
            requirements.add(TEST_TAG);
        }
        box.remove("id");
        box = client.createBox(box);
        testBoxData.setJson(box);
        return box;
    }

    static JSONObject createTestProfile(TestBoxData testBoxData, JSONObject testProvider, TemplateResolver resolver, Client client) throws Exception {
        JSONObject testProfile = createTestProfile(testBoxData.getJson(), testProvider, resolver, client);
        testBoxData.setNewProfileId(testProfile.getString("id"));
        return testProfile;
    }

    static JSONObject createTestProfile(JSONObject box, JSONObject testProvider, TemplateResolver resolver, Client client) throws Exception {
        JSONObject policyBox = JSONObject.fromObject(createTestDataFromTemplate("test-policy.json", new TemplateResolverImpl(client, resolver)));
        policyBox.put("name", NAME_PREFIX + UUID.randomUUID().toString());
        policyBox.put("owner", TestUtils.TEST_WORKSPACE);
        policyBox.put("provider_id", testProvider.getString("id"));
        JSONArray claims = JSONArray.fromObject(box.getJSONArray("requirements"));
        claims.add("test");
        policyBox.put("claims", claims);
        return client.createBox(policyBox);
    }

    private static JSONObject loadBox(String templatePath, TemplateResolver resolver) throws Exception {
        URI boxJsonUri = TestUtils.class.getResource(templatePath).toURI();
        String template = FileUtils.readFileToString(new File(boxJsonUri), "UTF-8");
        JSONObject box = JSONObject.fromObject(resolver.resolve(template));
        if (box.containsKey("variables")) {
            for (Object variable : box.getJSONArray("variables")) {
                JSONObject variableJson = (JSONObject) variable;
                String value = variableJson.getString("value");
                if (variableJson.getString("type").equals("File") && StringUtils.isNotBlank(value)) {
                    variableJson.put("value", boxJsonUri.resolve(value).toString());
                }
            }
        }
        if (box.containsKey("events")) {
            JSONObject events = box.getJSONObject("events");
            for (Object entry : events.entrySet()) {
                Map.Entry mapEntry = (Map.Entry) entry;
                events.put(mapEntry.getKey().toString(), boxJsonUri.resolve(mapEntry.getValue().toString()).toString());
            }
        }

        return box;
    }

    private static String createTestDataFromTemplate(String templatePath, TemplateResolver resolver) throws IOException {
        String template = TestUtils.getResourceAsString(templatePath);
        return resolver.resolve(template);
    }

}
