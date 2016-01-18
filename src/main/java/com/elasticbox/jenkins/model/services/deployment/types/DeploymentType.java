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


import com.elasticbox.jenkins.model.services.error.ServiceException;

/**
 * Created by serna on 1/14/16.
 */
public enum DeploymentType {

    APPLICATIONBOX_DEPLOYMENT_TYPE          ("ApplicationBox"),
    CLOUDFORMATIONTEMPLATE_DEPLOYMENT_TYPE  ("CloudformationTemplate"),
    CLOUDFORMATIONMANAGED_DEPLOYMENT_TYPE   ("CloudformationManaged"),
    SCRIPTBOX_DEPLOYMENT_TYPE               ("ScriptBox");

    private String id;


    DeploymentType(String id){
        this.id=id;
    }

    public String getId() {
        return id;
    }

    public static DeploymentType getType(String id) throws ServiceException {
        DeploymentType[] values = DeploymentType.values();
        for (DeploymentType boxType : values) {
            if(boxType.isType(id))
                return boxType;
        }
        throw new ServiceException("There is no DeploymentType with id: "+id);
    }

    public boolean isType(String id){
        return this.id.equals(id);
    }
}
