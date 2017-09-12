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

import static com.elasticbox.jenkins.model.repository.api.deserializer.Utils.filter;
import static com.elasticbox.jenkins.model.repository.api.deserializer.Utils.transform;

import com.elasticbox.ApiClient;
import com.elasticbox.jenkins.model.repository.WorkspaceRepository;
import com.elasticbox.jenkins.model.repository.api.deserializer.filter.Filter;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.workspaces.WorkspaceTransformer;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class WorkspacesRepositoryApiImpl implements WorkspaceRepository {

    private static final Logger logger = Logger.getLogger(WorkspacesRepositoryApiImpl.class.getName());

    private ApiClient client;

    public WorkspacesRepositoryApiImpl(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<AbstractWorkspace> getWorkspaces() throws RepositoryException {
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
                    }
                }
            );

            if (!filtered.isEmpty()) {
                return filtered.get(0);
            }

            return workspaces.get(0);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is no workspaces");
            throw new RepositoryException("There is no workspaces");
        } catch (NullPointerException npe) {
            logger.log(Level.SEVERE, "There is no client");
            throw new RepositoryException("There is no client");
        }

    }
}
