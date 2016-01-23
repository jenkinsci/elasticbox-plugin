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

package com.elasticbox.jenkins.model.services.deployment.configuration.policies;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/30/15.
 */
public class ApplicationBoxDeploymentDataPolicies extends AbstractDeploymentDataPoliciesHandler {

    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(BoxRepository boxRepository, String workspace, final AbstractBox boxToDeploy) throws RepositoryException {
        return new ArrayList<PolicyBox>();
    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        return boxToDeploy.getType() == BoxType.APPLICATION;
    }



}
