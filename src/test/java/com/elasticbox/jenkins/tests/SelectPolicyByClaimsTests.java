package com.elasticbox.jenkins.tests;

import hudson.model.FreeStyleBuild;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

/**
 *
 * @author Phong Nguyen Le
 */
public class SelectPolicyByClaimsTests extends BuildStepTestBase {

    @Test
    public void testDeployWithPolicyByClaims() throws Exception {
        final String testTag = UUID.randomUUID().toString().substring(0, 30);
        Map<String, String> testParameters = Collections.singletonMap("TEST_TAG", testTag);
        FreeStyleBuild build = TestUtils.runJob("test", createTestDataFromTemplate("jobs/test-policy-by-claims.xml"),
                testParameters, jenkins.getInstance());
        TestUtils.assertBuildSuccess(build);
        TestUtils.cleanUp(testTag, jenkins.getInstance());
    }
}
