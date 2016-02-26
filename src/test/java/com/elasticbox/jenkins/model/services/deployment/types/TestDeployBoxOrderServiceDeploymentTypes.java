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

package com.elasticbox.jenkins.model.services.deployment.types;

import com.elasticbox.ApiClient;
import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.api.BoxRepositoryApiImpl;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import net.sf.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDeployBoxOrderServiceDeploymentTypes {

    @Test
    public void testPolicyBasedDeploymentType() throws IOException, RepositoryException, ServiceException {

        testDeploymentType(UnitTestingUtils.getFakeScriptBox(), DeploymentType.SCRIPTBOX_DEPLOYMENT_TYPE);

    }

    @Test
    public void testApplicationBoxBasedDeploymentType() throws IOException, RepositoryException, ServiceException {

        testDeploymentType(UnitTestingUtils.getFakeApplicationBox(), DeploymentType.APPLICATIONBOX_DEPLOYMENT_TYPE);

    }

    @Test
    public void testCloudFormationManagedDeploymentType() throws IOException, RepositoryException, ServiceException {

        testDeploymentType(UnitTestingUtils.getFakeCloudFormationManagedBox(), DeploymentType.CLOUDFORMATIONMANAGED_DEPLOYMENT_TYPE);

    }

    private void testDeploymentType(JSONObject fakeBox, DeploymentType targetDeploymentType) throws IOException, ServiceException {

        final ApiClient api = mock(ApiClient.class);
        when(api.getBox(fakeBox.getString("id"))).thenReturn(fakeBox);

        final BoxRepository boxRepository = new BoxRepositoryApiImpl(api);

        final DeployBoxOrderServiceImpl deployBoxOrderService = new DeployBoxOrderServiceImpl(api);

        final DeploymentType deploymentType = deployBoxOrderService.deploymentType(fakeBox.getString("id"));

        assertTrue("Deployment type should be "+targetDeploymentType, deploymentType == targetDeploymentType);

    }

}
