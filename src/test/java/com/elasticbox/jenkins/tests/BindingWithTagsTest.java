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

import com.elasticbox.jenkins.DescriptorHelper;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import java.io.ByteArrayOutputStream;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class BindingWithTagsTest extends BuildStepTestBase {

    @Test
    public void testBindingWithTags() throws Exception {
        final String testTag = UUID.randomUUID().toString().substring(0, 30);
        Map<String, String> testParameters = Collections.singletonMap("TEST_TAG", testTag);
        FreeStyleBuild build = TestUtils.runJob("test", createTestDataFromTemplate("jobs/test-binding-with-tags.xml"), testParameters, jenkins.getInstance());
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        build.getLogText().writeLogTo(0, log);
        String logText = log.toString();
        Assertions.assertThat(build.getResult()).as(logText).isEqualTo(Result.FAILURE);
        JSONArray instances = DescriptorHelper.getInstances(Collections.singleton(testTag), cloud.name, TestUtils.TEST_WORKSPACE, true);
        Assertions.assertThat(logText).contains(
                MessageFormat.format("Binding ambiguity for binding variable ANY_BINDING with the following tags: {0}, {1} instances are found with those tags", testTag, instances.size()));

        // verify the bindings
        JSONObject testBindingBoxInstance = TestUtils.findInstance(instances, TestUtils.TEST_BINDING_BOX_NAME);
        Assert.assertNotNull(testBindingBoxInstance);

        JSONObject testLinuxBoxInstance = TestUtils.findInstance(instances, TestUtils.TEST_LINUX_BOX_NAME);
        Assert.assertNotNull(testLinuxBoxInstance);
        JSONObject bindingVariable = TestUtils.findVariable(testLinuxBoxInstance.getJSONArray("variables"), "ANY_BINDING");
        Assert.assertEquals(bindingVariable.toString(), testBindingBoxInstance.getString("id"), bindingVariable.getString("value"));

        JSONObject testNestedBoxInstance = TestUtils.findInstance(instances, TestUtils.TEST_NESTED_BOX_NAME);
        Assert.assertNotNull(testNestedBoxInstance);
        JSONArray variables = testNestedBoxInstance.getJSONArray("variables");
        bindingVariable = TestUtils.findVariable(variables, "REQUIRED_BINDING");
        Assert.assertEquals(bindingVariable.toString(), testLinuxBoxInstance.getString("id"), bindingVariable.getString("value"));
        bindingVariable = TestUtils.findVariable(variables, "ANY_BINDING", "nested");
        Assert.assertEquals(bindingVariable.toString(), testBindingBoxInstance.getString("id"), bindingVariable.getString("value"));

        TestUtils.cleanUp(testTag, jenkins.getInstance());
    }

}
