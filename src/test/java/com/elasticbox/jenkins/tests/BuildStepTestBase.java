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
import com.elasticbox.IProgressMonitor;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.After;
import org.junit.Before;

/**
 *
 * @author Phong Nguyen Le
 */
public class BuildStepTestBase extends TestBase {
    private static final Logger LOGGER = Logger.getLogger(BuildStepTestBase.class.getName());

    private String schemaVersion;
    private final List<TestBoxData> testBoxDataList = Arrays.asList(new TestBoxData[] {
        new TestBoxData("boxes/test-linux-box/test-linux-box.json", "9af0eb3a-4d4b-4110-8ed0-1cbb3d5b2744"),
        new TestBoxData("boxes/test-binding-box.json", "e14460b4-c288-46f4-8a45-bea58e492428"),
        new TestBoxData("boxes/test-nested-box.json", "e155115d-6e4e-4027-b4a7-89eb3ae6ef58"),
        new TestBoxData("boxes/test-deeply-nested-box.json", "74cd448d-1e1b-4afb-8d92-c11eab38c99a")
    });
    private Map<String, TestBoxData> testBoxDataLookup;
    protected String newTestBindingBoxInstanceId = TestUtils.TEST_BINDING_BOX_INSTANCE_ID;
    private JSONObject testProvider;
    private TestUtils.TemplateResolver templateResolver;
    
    @Before
    public void setupTestData() throws Exception {
        Client client = cloud.getClient();
        JSONObject workspace = (JSONObject) client.doGet(MessageFormat.format("/services/workspaces/{0}", TestUtils.TEST_WORKSPACE), false);
        schemaVersion = Client.getSchemaVersion(workspace.getString("schema"));
        testProvider = TestUtils.createTestProvider(client);
        testBoxDataLookup = new HashMap<String, TestBoxData>();
        templateResolver = new TestUtils.TemplateResolver() {

            public String resolve(String template) {
                template = template.replace("{schema_version}", schemaVersion).replace(TestUtils.DEFAULT_TEST_WORKSPACE,
                        TestUtils.TEST_WORKSPACE);
                for (TestBoxData testBoxData : testBoxDataList) {
                    try {
                        template = template.replace(testBoxData.boxId, testBoxData.getJson().getString("id")).
                                replace(testBoxData.profileId, testBoxData.getNewProfileId());
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                return template.replace(TestUtils.TEST_BINDING_BOX_INSTANCE_ID, newTestBindingBoxInstanceId);
            }
            
        };
                
        for (TestBoxData testBoxData : testBoxDataList) {
            testBoxDataLookup.put(testBoxData.getJson().getString("name"), testBoxData);
            TestUtils.createTestBox(testBoxData, templateResolver, client);
            TestUtils.createTestProfile(testBoxData, testProvider, templateResolver, client);
        }   
        TestBoxData testBindingBoxData = testBoxDataLookup.get(TestUtils.TEST_BINDING_BOX_NAME);
        JSONArray variables = new JSONArray();
        JSONObject variable = new JSONObject();
        variable.put("name", "CONNECTION");
        variable.put("type", "Text");
        variable.put("value", "connection");
        variables.add(variable);
        IProgressMonitor monitor = client.deploy(testBindingBoxData.getJson().getString("id"),
                testBindingBoxData.getNewProfileId(), testBindingBoxData.getJson().getString("owner"), 
                "jenkins-plugin-test", 1, variables, null, null);
        monitor.waitForDone(10);
        JSONObject testBindingBoxInstance = client.getInstance(Client.getResourceId(monitor.getResourceUrl()));
        newTestBindingBoxInstanceId = testBindingBoxInstance.getString("id");
    }
    
    @After
    public void cleanUp() throws Exception {
        Client client = cloud.getClient();
        if (!newTestBindingBoxInstanceId.equals(TestUtils.TEST_BINDING_BOX_INSTANCE_ID)) {
            try {
                IProgressMonitor monitor = client.terminate(newTestBindingBoxInstanceId);
                monitor.waitForDone(10);
                if (monitor.isDone()) {
                    client.delete(newTestBindingBoxInstanceId);
                } else {
                    // this will force-terminate
                    monitor = client.terminate(newTestBindingBoxInstanceId);
                    monitor.waitForDone(1);
                    client.delete(newTestBindingBoxInstanceId);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, MessageFormat.format("Cannot delete test instance {0}", newTestBindingBoxInstanceId), ex);
            }
        }
        
        for (int i = testBoxDataList.size() - 1; i > -1; i--) {
            TestBoxData testBoxData = testBoxDataList.get(i);
            if (testBoxData.getJson().containsKey("uri")) {
                client.doDelete(testBoxData.getJson().getString("uri"));
            }
        }
        
        if (testProvider != null) {
            client.doDelete(testProvider.getString("uri"));
        }
    }
    
    protected JSONObject getTestBox(String boxName) throws IOException {
        TestBoxData testBoxData = testBoxDataLookup.get(boxName);
        return testBoxData != null ? testBoxData.getJson() : null;
    }
    
    protected String createTestDataFromTemplate(String templatePath) throws IOException {
        String template = TestUtils.getResourceAsString(templatePath);
        return templateResolver.resolve(template);
    }
    
}
