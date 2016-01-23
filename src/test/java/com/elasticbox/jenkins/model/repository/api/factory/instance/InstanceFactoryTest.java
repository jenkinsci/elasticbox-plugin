/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.repository.api.factory.instance;

import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.TemplateCloudFormationBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.factory.box.*;
import net.sf.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by serna on 11/29/15.
 * Tests the JSON to Objects parsing
 */
public class InstanceFactoryTest {


    @Test
    public void testCreateInstance() throws ElasticBoxModelException {

        final Instance instance = new InstanceFactoryImpl().create(UnitTestingUtils.getFakeProcessingInstance());

        assertTrue("instance id was not set", instance.getId().equals("i-kbfgmo"));
        assertTrue("instance id was not set", instance.getBox().equals("388d5e7c-2e26-490f-adcf-37cf244ee27f"));
        assertTrue("instance id was not set", instance.getAutomaticUpdates() == Instance.AutomaticUpdates.OFF);
        assertTrue("instance id was not set", instance.getName().equals("scriptbox1"));
        assertTrue("instance id was not set", instance.getDeleted() == null);
        assertTrue("instance id was not set", instance.getState() == Instance.State.PROCESSING);
        assertTrue("instance id was not set", instance.getUri().equals("/services/instances/i-kbfgmo"));
        assertTrue("instance id was not set", instance.getOwner().equals("operations"));
        assertTrue("instance id was not set", instance.getSchema().equals("http://elasticbox.net/schemas/instance"));


    }


}