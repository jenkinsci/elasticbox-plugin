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

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.deployment.execution.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;

import java.util.List;

/**
 * Created by serna on 1/29/16.
 */
public class BoxFillOptionsCommand extends  AbstractFillOptionsCommand{

    private AbstractBox defaultBox;

    public BoxFillOptionsCommand(Type handledType) {
        super(FillOptionsCommand.Type.BOX);
    }

    @Override
    public ListBoxModel perform(FillOptionsContext context) {
        final String cloudName = context.getParameter(ParameterType.CLOUD_NAME);
        final String workspaceId = context.getParameter(ParameterType.WORKSPACE_ID);
        if(StringUtils.isBlank(cloudName) || StringUtils.isBlank(workspaceId)){
            //TODO
        }

        final DeployBoxOrderResult<List<AbstractBox>> boxesToDeployResult = context.getDeployBoxOrderService().getBoxesToDeploy(workspaceId);
        ListBoxModel boxes = new ListBoxModel();
        for(AbstractBox box: boxesToDeployResult.getResult()){
            boxes.add(box.getName(),box.getId());
        }

        return boxes;

    }

    @Override
    public void init(FillOptionsContext context) {

    }

    public boolean isInitialized(){
        return defaultBox != null;
    }
}
