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
import hudson.model.FreeStyleBuild;
import java.util.Collections;
import java.util.UUID;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import net.sf.json.JSONObject;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class CompatibilityTest extends BuildStepTestBase {

    @Test
    public void testBuildWithOldSteps() throws Exception {
        String testParameter = UUID.randomUUID().toString();
        String projectXml = createTestDataFromTemplate("jobs/test-old-job.xml");
        FreeStyleBuild build = TestUtils.runJob("test-old-job", projectXml,
                Collections.singletonMap("eb_test_build_parameter", testParameter), jenkinsRule.getInstance());
        TestUtils.assertBuildSuccess(build);

        Client client = cloud.getClient();
        JSONObject instance = client.getInstance(newTestBindingBoxInstanceId);
        JSONObject connectionVar = null;
        JSONObject httpsVar = null;
        for (Object json : instance.getJSONArray("variables")) {
            JSONObject variable = (JSONObject) json;
            String name = variable.getString("name");
            if (name.equals("CONNECTION")) {
                connectionVar = variable;
            } else if (name.equals("HTTPS")) {
                httpsVar = variable;
            }
        }

        assertEquals(connectionVar.toString(), testParameter, connectionVar.getString("value"));
        assertFalse(httpsVar.toString(), httpsVar.getString("value").equals("${BUILD_ID}"));
    }

}
