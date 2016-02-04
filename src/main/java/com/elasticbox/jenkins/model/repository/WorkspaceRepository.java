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

package com.elasticbox.jenkins.model.repository;

import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.workspace.AbstractWorkspace;

import java.util.List;

/**
 * Created by serna on 1/28/16.
 */
public interface WorkspaceRepository {

    List<AbstractWorkspace> getWorkspaces() throws RepositoryException;
}
