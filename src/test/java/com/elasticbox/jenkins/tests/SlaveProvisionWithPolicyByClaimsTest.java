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

package com.elasticbox.jenkins.tests;

import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.SlaveConfiguration;
import hudson.model.Node;
import java.io.IOException;
import java.util.UUID;
import net.sf.json.JSONArray;
import org.junit.Test;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveProvisionWithPolicyByClaimsTest extends SlaveProvisionTestBase {

    @Override
    protected SlaveConfiguration createSlaveConfiguration(String slaveBoxName, JSONArray variables) throws IOException {
        TestBoxData testBoxData = testBoxDataLookup.get(slaveBoxName);
        return new SlaveConfiguration(UUID.randomUUID().toString(), TestUtils.TEST_WORKSPACE,
                testBoxData.getJson().getString("id"), DescriptorHelper.LATEST_BOX_VERSION,
                null, "linux, test", null, null, 1, 2, slaveBoxName, variables.toString(),
                UUID.randomUUID().toString(), "", null, Node.Mode.NORMAL, 0, null, 1, 60);
    }


    @Test
    public void testSlaveProvision() throws Exception {
        provisionSlaves();
    }

}
