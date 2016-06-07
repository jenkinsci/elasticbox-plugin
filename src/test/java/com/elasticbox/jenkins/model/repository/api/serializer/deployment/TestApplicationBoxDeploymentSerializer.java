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

package com.elasticbox.jenkins.model.repository.api.serializer.deployment;

import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.builders.InstanceExpirationSchedule;
import com.elasticbox.jenkins.model.services.deployment.execution.context.ApplicationBoxDeploymentContext;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;

import static org.junit.Assert.assertTrue;

public class TestApplicationBoxDeploymentSerializer {

    @Test
    public void testApplicationBoxDeploymentRequestSerializerNotLatest() throws ParseException, IOException {

        final ElasticBoxCloud elasticBoxCloud = Mockito.mock(ElasticBoxCloud.class);

        final InstanceExpirationSchedule expiration = new InstanceExpirationSchedule("terminate", null, "01/25/2016", "16:00") {
        };

        final ApplicationBoxDeploymentContext applicationBoxDeploymentContext = new ApplicationBoxDeploymentContext.Builder()
                .box("FAKE_BOX_ID")
                .boxVersion("NOT_LATEST")
                .expirationTime(expiration.getUtcDateTime())
                .expirationOperation(expiration.getOperation())
                .requirements(new String[]{"FAKE_CLAIM"})
                .tags(new HashSet<String>() {{
                    add("FAKE_TAG");
                }})
                .name("FAKE_NAME")
                .owner("FAKE_OWNER")
                .waitForDone(true)
                .cloud(elasticBoxCloud)
                .build();

        final JSONObject request = new ApplicationBoxDeploymentSerializer().createRequest(applicationBoxDeploymentContext);
        assertTrue("policyBox id was not set", request.getJSONObject("lease").getString("expire").equals(expiration.getUtcDateTime()));
        assertTrue("policyBox id was not set", request.getJSONObject("lease").getString("operation").equals("terminate"));
        assertTrue("policyBox id was not set", request.getJSONObject("box").getString("id").equals("FAKE_BOX_ID"));
        assertTrue("policyBox id was not set", request.getJSONArray("instance_tags").get(0).equals("FAKE_TAG"));
        assertTrue("policyBox id was not set", request.getJSONArray("requirements").get(0).equals("FAKE_CLAIM"));
        assertTrue("policyBox id was not set", request.getString("schema").equals("http://elasticbox.net/schemas/deploy/application"));
        assertTrue("policyBox id was not set", request.getString("owner").equals("FAKE_OWNER"));
        assertTrue("policyBox id was not set", request.getString("name").equals("FAKE_NAME"));
    }
}
