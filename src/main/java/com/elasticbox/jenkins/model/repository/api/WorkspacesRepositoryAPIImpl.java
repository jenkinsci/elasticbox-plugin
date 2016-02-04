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

package com.elasticbox.jenkins.model.repository.api;

import com.elasticbox.APIClient;
import com.elasticbox.jenkins.model.repository.WorkspaceRepository;
import com.elasticbox.jenkins.model.repository.api.factory.workspace.WorkspaceFactoryImpl;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 1/28/16.
 */
public class WorkspacesRepositoryAPIImpl implements WorkspaceRepository {

    private static final Logger logger = Logger.getLogger(WorkspacesRepositoryAPIImpl.class.getName());

    private APIClient client;

    public WorkspacesRepositoryAPIImpl(APIClient client) {
        this.client = client;
    }

    @Override
    public List<AbstractWorkspace> getWorkspaces() throws RepositoryException{
        try {
            List<AbstractWorkspace> workspaces =  new ArrayList<>();
            final JSONArray workspacesArray = client.getWorkspaces();
            if(!workspacesArray.isEmpty()){
                for (Object workspaceJsonObject : workspacesArray) {
                    JSONObject workspaceJson = (JSONObject) workspaceJsonObject;
                    final AbstractWorkspace abstractWorkspace = new WorkspaceFactoryImpl().create(workspaceJson);
                    workspaces.add(abstractWorkspace);
                }
            }
            return workspaces;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving workspaces");
            throw new RepositoryException("There is an error retrieving workspaces");
        }
    }
}
