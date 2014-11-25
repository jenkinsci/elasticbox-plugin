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

import com.elasticbox.jenkins.builders.InstanceExpirationSchedule;
import hudson.model.AbstractBuild;
import java.util.Calendar;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class InstanceExpirationTest extends ComputeServiceTestBase {

    @Override
    public void setupTestData() throws Exception {
        super.setupTestData();
        
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        templateResolver.map("{expiration-date}", InstanceExpirationSchedule.DATE_FORMAT.format(calendar.getTime()));
        templateResolver.map("{expiration-time", "00:00");
    }

    @Test
    public void testInstanceExpiration() throws Exception {
        runTestJob("jobs/test-instance-expiration.xml");
    }    

    @Override
    protected void validate(AbstractBuild build) throws Exception {
        String testTag = (String) build.getBuildVariableResolver().resolve("TEST_TAG");
        for (Object instance : cloud.getClient().getInstances(TestUtils.TEST_WORKSPACE)) {
            JSONObject instanceJson = (JSONObject) instance;
            JSONArray tags = instanceJson.getJSONArray("tags");
            if (tags.contains(testTag)) {
                if (tags.contains("always-on")) {
                    Assert.assertFalse(instanceJson.containsKey("lease"));                    
                } else {
                    JSONObject lease = instanceJson.getJSONObject("lease");
                    if (tags.contains("shutdown")) {
                        Assert.assertEquals("shutdown", lease.getString("operation"));                        
                    } else if (tags.contains("terminate")) {
                        Assert.assertEquals("terminate", lease.getString("operation"));
                    } else {
                        throw new AssertionError("Missing expiration tag that is one of the following: always-on, shutdown, terminate");
                    }
                }
            }
         }        
    }
    
}
