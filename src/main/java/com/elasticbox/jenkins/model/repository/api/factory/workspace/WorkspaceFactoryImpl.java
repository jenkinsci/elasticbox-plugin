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

package com.elasticbox.jenkins.model.repository.api.factory.workspace;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.factory.JSONFactoryUtils;
import com.elasticbox.jenkins.model.repository.api.factory.box.GenericBoxFactory;
import com.elasticbox.jenkins.model.repository.api.factory.instance.InstanceFactory;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;
import com.elasticbox.jenkins.model.workspace.PersonalWorkspace;
import com.elasticbox.jenkins.model.workspace.TeamWorkspace;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public class WorkspaceFactoryImpl implements WorkspaceFactory<AbstractWorkspace> {
    @Override
    public AbstractWorkspace create(JSONObject jsonObject) throws ElasticBoxModelException {

        final AbstractWorkspace.WorkspaceType workspaceType = AbstractWorkspace.WorkspaceType.findBy(jsonObject.getString("schema"));
        if (workspaceType == AbstractWorkspace.WorkspaceType.PERSONAL){
            return new PersonalWorkspace.PersonalWorkspaceBuilder()
                    .withId(jsonObject.getString("id"))
                    .withSchema(jsonObject.getString("schema"))
                    .withName(jsonObject.getString("name"))
                    .withType(workspaceType)
                    .withOrganization(jsonObject.getString("organization"))
                    .withEmail(jsonObject.getString("email"))
                    .build();
        }

        return new TeamWorkspace.TeamWorkspaceBuilder()
                .withId(jsonObject.getString("id"))
                .withSchema(jsonObject.getString("schema"))
                .withName(jsonObject.getString("name"))
                .withType(workspaceType)
                .withOrganizations((String[]) jsonObject.getJSONArray("organizations").toArray(new String[jsonObject.getJSONArray("organizations").size()]))
                .build();

    }

}
