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
import com.elasticbox.jenkins.ElasticBoxCloud;
import static com.elasticbox.jenkins.tests.TestUtils.getResourceAsString;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Phong Nguyen Le
 */
public class TestBase {
    private static final String TEST_PROVIDER_TYPE = "Test Provider";
    private static final String NAME_PREFIX = "jenkins-plugin-test-";

    static class TestBoxData {
        public final String jsonFileName;
        public final String profileId;
        public final String boxId;
        private JSONObject json;
        private String newProfileId;

        public TestBoxData(String jsonFileName, String profileId) {
            this.jsonFileName = jsonFileName;
            this.profileId = this.newProfileId = profileId;
            try {
                json = JSONObject.fromObject(getResourceAsString(jsonFileName));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            boxId = json.getString("id");
        }
        
        public JSONObject getJson() throws IOException {
            if (json == null) {
                json = JSONObject.fromObject(getResourceAsString(jsonFileName));
            }
            return json;
        }
        
        public void setJson(JSONObject json) {
            this.json = json;
        }

        public String getNewProfileId() {
            return newProfileId;
        }

        public void setNewProfileId(String newProfileId) {
            this.newProfileId = newProfileId;
        }

    }       

    protected ElasticBoxCloud cloud;    
    private String schemaVersion;
    private final List<TestBoxData> testBoxDataList = Arrays.asList(new TestBoxData[] {
        new TestBoxData("test-linux-box.json", "9af0eb3a-4d4b-4110-8ed0-1cbb3d5b2744"),
        new TestBoxData("test-binding-box.json", "e14460b4-c288-46f4-8a45-bea58e492428"),
        new TestBoxData("test-nested-box.json", "e155115d-6e4e-4027-b4a7-89eb3ae6ef58")
    });
    private Map<String, TestBoxData> testBoxDataLookup;
    protected String newTestBindingBoxInstanceId = TestUtils.TEST_BINDING_BOX_INSTANCE_ID;
    private JSONObject testProvider;
    
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    
    @Before
    public void setupTestData() throws Exception {
        cloud = new ElasticBoxCloud("elasticbox", TestUtils.ELASTICBOX_URL, 2, 10, TestUtils.USER_NAME, TestUtils.PASSWORD, Collections.EMPTY_LIST);
        jenkins.getInstance().clouds.add(cloud);        
        Client client = cloud.createClient();
        JSONObject workspace = (JSONObject) client.doGet(MessageFormat.format("/services/workspaces/{0}", TestUtils.TEST_WORKSPACE), false);
        schemaVersion = Client.getSchemaVersion(workspace.getString("schema"));
        createTestProvider(client);
        testBoxDataLookup = new HashMap<String, TestBoxData>();
        for (TestBoxData testBoxData : testBoxDataList) {
            testBoxDataLookup.put(testBoxData.getJson().getString("name"), testBoxData);
            JSONObject box = JSONObject.fromObject(createTestDataFromTemplate(testBoxData.jsonFileName));
            box.put("name", box.getString("name") + '-' + UUID.randomUUID().toString());
            box.put("owner", TestUtils.TEST_WORKSPACE);
            box.remove("id");
            box = client.createBox(box);
            testBoxData.setJson(box);
            createTestProfile(testBoxData, client);
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
                "jenkins-plugin-test", 1, variables);
        monitor.waitForDone(10);
        JSONObject testBindingBoxInstance = client.getInstance(Client.getResourceId(monitor.getResourceUrl()));
        newTestBindingBoxInstanceId = testBindingBoxInstance.getString("id");
    }
    
    @After
    public void cleanUp() throws Exception {
        Client client = cloud.createClient();
        if (!newTestBindingBoxInstanceId.equals(TestUtils.TEST_BINDING_BOX_INSTANCE_ID)) {
            IProgressMonitor monitor = client.terminate(newTestBindingBoxInstanceId);
            monitor.waitForDone(10);
            client.delete(newTestBindingBoxInstanceId);
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
    
    private void createTestProvider(Client client) throws IOException {
        testProvider = new JSONObject();
        testProvider.put("name", NAME_PREFIX + UUID.randomUUID().toString());
        testProvider.put("schema", MessageFormat.format("{0}{1}/test/provider", Client.BASE_ELASTICBOX_SCHEMA, schemaVersion));
        testProvider.put("type", TEST_PROVIDER_TYPE);
        testProvider.put("icon", "images/platform/provider.png");
        testProvider.put("secret", "secret");
        testProvider.put("owner", TestUtils.TEST_WORKSPACE);
        IProgressMonitor monitor = client.createProvider(testProvider);
        monitor.waitForDone(10);
        testProvider = (JSONObject) client.doGet(monitor.getResourceUrl(), false);        
    }
    
    private JSONObject createTestProfile(TestBoxData testBoxData, Client client) throws IOException {
        JSONObject testProfile = JSONObject.fromObject(createTestDataFromTemplate("test-profile.json"));
        testProfile.remove("id");
        testProfile.put("name", NAME_PREFIX + UUID.randomUUID().toString());
        testProfile.put("owner", TestUtils.TEST_WORKSPACE);
        testProfile.put("provider", testProvider.getString("name"));
        JSONObject profileBox = testProfile.getJSONObject("box");
        profileBox.put("version", testBoxData.getJson().getString("id"));
        profileBox.put("name", testBoxData.getJson().getString("name"));
        testProfile = client.doPost("/services/profiles", testProfile);
        testBoxData.setNewProfileId(testProfile.getString("id"));
        return testProfile;
    }
    
    protected String createTestDataFromTemplate(String templatePath) throws IOException {
        String template = TestUtils.getResourceAsString(templatePath).replace("{schema_version}", schemaVersion);
        for (TestBoxData testBoxData : testBoxDataList) {
            template = template.replace(testBoxData.boxId, testBoxData.getJson().getString("id")).
                    replace(testBoxData.profileId, testBoxData.getNewProfileId());
        }
        return template.replace(TestUtils.TEST_BINDING_BOX_INSTANCE_ID, newTestBindingBoxInstanceId);
    }
    
}