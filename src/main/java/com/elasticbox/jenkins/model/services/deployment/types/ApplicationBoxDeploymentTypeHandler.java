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

import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.DeploymentValidationResult;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by serna on 11/30/15.
 */
public class ApplicationBoxDeploymentTypeHandler extends AbstractDeploymentTypeHandler {

    public ApplicationBoxDeploymentTypeHandler() {
        super(DeploymentType.APPLICATIONBOX_DEPLOYMENT_TYPE);
    }

    @Override
    public List<PolicyBox> retrievePoliciesToDeploy(BoxRepository boxRepository, String workspace, final AbstractBox boxToDeploy) throws RepositoryException {
        return new ArrayList<PolicyBox>();
    }

    @Override
    public boolean canManage(AbstractBox boxToDeploy) {
        return boxToDeploy.getType() == BoxType.APPLICATION;
    }


    @Override
    public DeploymentValidationResult validateDeploymentData(DeployBox deployData) {

        final String claims = deployData.getClaims();
        final Set<String> claimsSet = new HashSet<String>();
        if (StringUtils.isNotEmpty(claims)){
            for (String tag : claims.split(",")) {
                claimsSet.add(tag.trim());
            }
        }

        return new DeploymentValidationResult() {
            @Override
            public boolean isOk() {
                return true;
            }

            @Override
            public List<Cause> causes() {
                return new ArrayList<>();
            }

            @Override
            public DeploymentData getDeploymentData() {
                return new ApplicationBoxDeploymentData(claimsSet);
            }
        };
    }

    private class ApplicationBoxDeploymentData implements DeploymentValidationResult.DeploymentData {

        private DeploymentType deploymentType = DeploymentType.APPLICATIONBOX_DEPLOYMENT_TYPE;
        private Set<String> requirements;

        public ApplicationBoxDeploymentData(Set<String> requirements) {
            this.requirements = requirements;
        }

        public DeploymentType getDeploymentType() {
            return deploymentType;
        }

        public Set<String> getRequirements() {
            return requirements;
        }
    }


}
