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
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;

/**
 *
 * @author Phong Nguyen Le
 */
public class BuildStepTestBase extends TestBase {
    private String schemaVersion;
    private final List<TestBoxData> testBoxDataList = Arrays.asList(new TestBoxData[] {
        new TestBoxData("boxes/test-linux-box/test-linux-box.json", "com.elasticbox.jenkins.tests.boxes.test-linux-box.test-profile"),
        new TestBoxData("boxes/test-binding-box.json", "com.elasticbox.jenkins.tests.boxes.test-binding-box.test-profile"),
        new TestBoxData("boxes/test-nested-box.json", "com.elasticbox.jenkins.tests.boxes.test-nested-box.test-profile"),
        new TestBoxData("boxes/test-deeply-nested-box.json", "com.elasticbox.jenkins.tests.boxes.test-deeply-nested-box.test-profile")
    });
    protected Map<String, TestBoxData> testBoxDataLookup;
    protected String newTestBindingBoxInstanceId = TestUtils.TEST_BINDING_BOX_INSTANCE_ID;
    protected JSONObject testProvider;
    private TestUtils.TemplateResolver templateResolver;
    
    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        Client client = cloud.getClient();
        JSONObject workspace = (JSONObject) client.doGet(MessageFormat.format("/services/workspaces/{0}", TestUtils.TEST_WORKSPACE), false);
        schemaVersion = Client.getSchemaVersion(workspace.getString("schema"));
        testProvider = TestUtils.createTestProvider(client);
        testBoxDataLookup = new HashMap<String, TestBoxData>();
        templateResolver = createTemplateResolver();
                
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
        deleteAfter(testBindingBoxInstance);
        newTestBindingBoxInstanceId = testBindingBoxInstance.getString("id");
    }
    
    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        
        Client client = cloud.getClient();

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
    
    protected class TemplateResolveImpl implements TestUtils.TemplateResolver {
        public String resolve(String template) {
            String finalSchemaVersion = StringUtils.isBlank(schemaVersion) ? schemaVersion : (schemaVersion + "/");
            template = template.replace("{schema_version}", finalSchemaVersion).replace(TestUtils.DEFAULT_TEST_WORKSPACE,
                    TestUtils.TEST_WORKSPACE);
            for (TestBoxData testBoxData : testBoxDataList) {
                try {
                    template = template.replace(testBoxData.profileId, testBoxData.getNewProfileId())
                            .replace(testBoxData.boxId, testBoxData.getJson().getString("id"));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return template.replace(TestUtils.TEST_BINDING_BOX_INSTANCE_ID, newTestBindingBoxInstanceId);
        }        
    }
    
    protected TestUtils.TemplateResolver createTemplateResolver() {
        return new TemplateResolveImpl();
    }

    protected TestUtils.TemplateResolver getTemplateResolver() {
        return templateResolver;
    }
    
}
