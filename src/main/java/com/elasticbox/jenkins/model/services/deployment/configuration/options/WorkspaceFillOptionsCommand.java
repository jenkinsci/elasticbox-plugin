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
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;
import hudson.util.ListBoxModel;

/**
 * Created by serna on 1/29/16.
 */
public class WorkspaceFillOptionsCommand implements FillOptionsCommand{


    @Override
    public ListBoxModel perform(FillOptionsContext fillOptionsContext) {
    //TODO
        return null;

    }

    @Override
    public void init(FillOptionsContext context) {

    }

    @Override
    public boolean isInitialized() {
        return false;
    }
}
