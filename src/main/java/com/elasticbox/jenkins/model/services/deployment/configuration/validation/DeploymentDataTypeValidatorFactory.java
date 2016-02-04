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

package com.elasticbox.jenkins.model.services.deployment.configuration.validation;

import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by serna on 11/30/15.
 */
public abstract class DeploymentDataTypeValidatorFactory {

    private static DeploymentDataTypeValidator[] deploymentDataTypesValidators = new DeploymentDataTypeValidator[]{
                new CloudFormationManagedAbstractDeploymentDataValidator(),
                new CloudFormationTemplateAbstractDeploymentDataValidator(),
                new ApplicationBoxAbstractDeploymentDataValidator(),
                new PolicyBasedAbstractDeploymentDataValidator()
    };

    public static DeploymentDataTypeValidator createValidator(final DeploymentType deploymentType) throws ServiceException {

        final DeploymentDataTypeValidator validator = firstMatch(new DeploymentTypeCondition() {
            @Override
            public boolean comply(DeploymentDataTypeValidator validator) {
                return validator.getManagedType() == deploymentType;
            }
        });

        return validator;
    }

    private static DeploymentDataTypeValidator firstMatch(DeploymentTypeCondition condition) throws ServiceException {
        for (DeploymentDataTypeValidator validator : deploymentDataTypesValidators) {
            if (condition.comply(validator)){
                return validator;
            }
        }
        throw new ServiceException("There is no AbstractDeploymentDataTypeValidatorFactory for this criteria");

    }


    interface DeploymentTypeCondition{
            boolean comply(DeploymentDataTypeValidator validator);
    }
}
