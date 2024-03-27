package com.elasticbox.jenkins.model.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.services.error.ServiceException;

public enum DeploymentType {

    CONTAINER_DEPLOYMENT_TYPE("Container"),
    APPLICATIONBOX_DEPLOYMENT_TYPE("ApplicationBox"),
    CLOUDFORMATION_DEPLOYMENT_TYPE("Cloudformation"),
    SCRIPTBOX_DEPLOYMENT_TYPE("ScriptBox");

    private String value;


    DeploymentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static DeploymentType findBy(AbstractBox box) throws ServiceException {

        switch (box.getType()) {
            case APPLICATION:
                return APPLICATIONBOX_DEPLOYMENT_TYPE;

            case CLOUDFORMATION:
                return CLOUDFORMATION_DEPLOYMENT_TYPE;

            case DOCKER:
                return CONTAINER_DEPLOYMENT_TYPE;

            case SCRIPT:
                return SCRIPTBOX_DEPLOYMENT_TYPE;

            default:
                throw new ServiceException("There is no DeploymentType for box: " + box.getType());
        }
    }

    public static DeploymentType findBy(String value) throws ServiceException {
        DeploymentType[] values = DeploymentType.values();
        for (DeploymentType deploymentType : values) {
            if (deploymentType.getValue().equals(value)) {
                return deploymentType;
            }
        }
        throw new ServiceException("There is no DeploymentType with id: " + value);
    }
}

