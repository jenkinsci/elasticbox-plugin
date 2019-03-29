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
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.ObjectFilter;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

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
        FreeStyleBuild build = TestUtils.runJob("test", createTestDataFromTemplate("jobs/test-binding-with-tags.xml"), testParameters, jenkinsRule.getInstance());

        ByteArrayOutputStream log = new ByteArrayOutputStream();
        build.getLogText().writeLogTo(0, log);
        String logText = log.toString();
        Assertions.assertThat(build.getResult()).as(logText).isEqualTo(Result.SUCCESS);

        JSONArray instances = getInstances(Collections.singleton(testTag), cloud.name, TestUtils.TEST_WORKSPACE);

        // verify the bindings
        JSONObject testBindingBoxInstance = TestUtils.findInstance(instances, TestUtils.TEST_BINDING_BOX_NAME);
        Assert.assertNotNull(testBindingBoxInstance);

        JSONObject testLinuxBoxInstance = TestUtils.findInstance(instances, TestUtils.TEST_LINUX_BOX_NAME);
        Assert.assertNotNull(testLinuxBoxInstance);

        JSONArray bindings = testLinuxBoxInstance.getJSONArray("bindings");
        Assert.assertEquals(
            MessageFormat.format(
                "Number of bindings unexpected: Found {0}, expected: {1}", bindings.size(), 1),bindings.size(), 1);

        Assert.assertTrue(
            MessageFormat.format(
                "Instance is not a binding: {0}", testBindingBoxInstance.getString("id")),
            bindings.getJSONObject(0).getJSONArray("instances").contains(testBindingBoxInstance.getString("id")));

        JSONObject testNestedBoxInstance = TestUtils.findInstance(instances, TestUtils.TEST_NESTED_BOX_NAME);
        Assert.assertNotNull(testNestedBoxInstance);

        bindings = testNestedBoxInstance.getJSONArray("bindings");
        Assert.assertEquals(
            MessageFormat.format("Number of bindings unexpected: Found {0}, expected: {1}", bindings.size(), 2),bindings.size(), 2);

        TestUtils.cleanUp(testTag, jenkinsRule.getInstance());
    }

    private JSONArray getInstances(Set<String> tags, String cloud, String workspace) throws IOException {
        Client client = ClientCache.getClient(cloud);
        JSONArray instances = DescriptorHelper.getInstances(client, workspace, new DescriptorHelper.InstanceFilterByTags(tags, true));
        List<String> instanceIDS = new ArrayList<>();
        for (Object instanceJson : instances) {
            JSONObject instance = (JSONObject) instanceJson;
            instanceIDS.add(instance.getString("id"));
        }

        return client.getInstances(workspace, instanceIDS);
    }

}
