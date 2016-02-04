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

package com.elasticbox.jenkins.model.services.deployment.configuration.options;

import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderService;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;

import java.util.EnumMap;
import java.util.HashMap;

/**
 * Created by serna on 1/29/16.
 */
public class FillOptionsContext {

    private EnumMap<ParameterType, String> parameters = new EnumMap<ParameterType, String>(ParameterType.class);

    private FillOptionsCommand.Type fillingtype;

    private ElasticBoxCloud defaultCloud;
    private AbstractBox abstractBox;
    private AbstractWorkspace workspace;

    private DeployBoxOrderService deployBoxOrderService;

    public String getParameter(ParameterType parameterType){
        return parameters.get(parameterType);
    }

    public DeployBoxOrderService getDeployBoxOrderService() {
        return deployBoxOrderService;
    }

    public ElasticBoxCloud getDefaultCloud(){
        return null;
    }

    public AbstractBox getAbstractBox() {
        return abstractBox;
    }

    public AbstractWorkspace getWorkspace() {
        return workspace;
    }

    public void setDefaultCloud(ElasticBoxCloud defaultCloud) {
        this.defaultCloud = defaultCloud;
    }

    public void setAbstractBox(AbstractBox abstractBox) {
        this.abstractBox = abstractBox;
    }

    public void setWorkspace(AbstractWorkspace workspace) {
        this.workspace = workspace;
    }

    public FillOptionsCommand.Type getFillingtype() {
        return fillingtype;
    }
}
