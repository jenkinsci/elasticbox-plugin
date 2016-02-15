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
import com.elasticbox.jenkins.model.repository.api.deserializer.Utils;
import com.elasticbox.jenkins.model.repository.api.deserializer.filter.Filter;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.workspaces.WorkspaceTransformer;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.elasticbox.jenkins.model.repository.api.deserializer.Utils.filter;
import static com.elasticbox.jenkins.model.repository.api.deserializer.Utils.transform;

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
            return transform(client.getWorkspaces(), new WorkspaceTransformer());
      } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving workspaces");
            throw new RepositoryException("There is an error retrieving workspaces");
        }
    }

    @Override
    public AbstractWorkspace findWorkspaceOrFirstByDefault(final String workspace) throws RepositoryException {

        final List<AbstractWorkspace> workspaces;
        try {
            workspaces = transform(client.getWorkspaces(), new WorkspaceTransformer());
            final List<AbstractWorkspace> filtered = filter(workspaces, new Filter<AbstractWorkspace>() {
                        @Override
                        public boolean apply(AbstractWorkspace it) {
                            return it.getId().equals(workspace);
                        }}
            );

            if(!filtered.isEmpty()){
                return filtered.get(0);
            }

            return workspaces.get(0);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is no workspaces");
            throw new RepositoryException("There is no workspaces");
        }

    }
}
