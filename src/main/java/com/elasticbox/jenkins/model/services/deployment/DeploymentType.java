package com.elasticbox.jenkins.model.services.deployment;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.services.error.ServiceException;

/**
 * Created by serna on 11/30/15.
 */

public enum DeploymentType {

    CONTAINER_DEPLOYMENT_TYPE               ("Container"),
    APPLICATIONBOX_DEPLOYMENT_TYPE          ("ApplicationBox"),
    CLOUDFORMATIONTEMPLATE_DEPLOYMENT_TYPE  ("CloudformationTemplate"),
    CLOUDFORMATIONMANAGED_DEPLOYMENT_TYPE   ("CloudformationManaged"),
    SCRIPTBOX_DEPLOYMENT_TYPE               ("ScriptBox");

    private String value;


    DeploymentType(String value){
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
                final CloudFormationBoxType cloudFormationType = ((CloudFormationBox) box).getCloudFormationType();
                if(cloudFormationType == CloudFormationBoxType.MANAGED){
                    return CLOUDFORMATIONMANAGED_DEPLOYMENT_TYPE;
                }
                return CLOUDFORMATIONTEMPLATE_DEPLOYMENT_TYPE;

            case DOCKER:
                return CONTAINER_DEPLOYMENT_TYPE;

            case SCRIPT:
                return SCRIPTBOX_DEPLOYMENT_TYPE;
        }

        throw new ServiceException("There is no DeploymentType for box: "+box.getType());
    }

    public static DeploymentType findBy(String value) throws ServiceException {
        DeploymentType[] values = DeploymentType.values();
        for (DeploymentType deploymentType : values) {
            if(deploymentType.getValue().equals(value)) {
                return deploymentType;
            }
        }
        throw new ServiceException("There is no DeploymentType with id: "+value);
    }

}

