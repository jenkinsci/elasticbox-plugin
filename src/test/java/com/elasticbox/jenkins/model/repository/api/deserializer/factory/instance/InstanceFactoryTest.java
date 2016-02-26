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

package com.elasticbox.jenkins.model.repository.api.deserializer.factory.instance;

import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.instances.InstanceTransformer;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class InstanceFactoryTest {


    @Test
    public void testCreateInstance() throws ElasticBoxModelException {

        final Instance instance = new InstanceTransformer().apply(UnitTestingUtils.getFakeProcessingInstance());

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