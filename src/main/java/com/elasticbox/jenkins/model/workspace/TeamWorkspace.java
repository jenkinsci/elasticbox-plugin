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

package com.elasticbox.jenkins.model.workspace;

public class TeamWorkspace extends AbstractWorkspace {

    private String owner;
    private String [] organizations;

    public TeamWorkspace(TeamWorkspaceBuilder teamWorkspaceBuilder) {
        super(teamWorkspaceBuilder);
        this.organizations = teamWorkspaceBuilder.organizations;
        this.owner = teamWorkspaceBuilder.owner;
    }

    public static class TeamWorkspaceBuilder extends ComplexBuilder<TeamWorkspaceBuilder, TeamWorkspace> {

        private String owner;
        private String [] organizations;

        public TeamWorkspaceBuilder() {
            this.type = WorkspaceType.PERSONAL;
        }

        public TeamWorkspaceBuilder withOwner(String owner) {
            this.owner = owner;
            return getThis();
        }

        public TeamWorkspaceBuilder withOrganizations(String [] organizations) {
            this.organizations = organizations;
            return getThis();
        }

        @Override
        public TeamWorkspace build() {
            return new TeamWorkspace(this);
        }
    }
}
