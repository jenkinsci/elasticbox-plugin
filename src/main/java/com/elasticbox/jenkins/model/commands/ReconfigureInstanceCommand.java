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

package com.elasticbox.jenkins.model.commands;

import com.elasticbox.Constants;

/**
 * Created by serna on 1/8/16.
 */
public class ReconfigureInstanceCommand extends AbstractCommand {


    @Override
    boolean canHandle(DeploymentContext context) {

        final String action =  context.getAlternateAction();

        if (!action.equals(Constants.INSTANCE_ACTION_NONE) && action.equals(Constants.INSTANCE_ACTION_RECONFIGURE)) {
            return true;
        }

        return false;
    }


}


