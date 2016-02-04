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

/**
 * Created by serna on 1/28/16.
 */
public class PersonalWorkspace extends AbstractWorkspace {

    private String email;
    private String organization;

    public PersonalWorkspace(PersonalWorkspaceBuilder personalWorkspaceBuilder) {
        super(personalWorkspaceBuilder);
        this.email = personalWorkspaceBuilder.email;
        this.organization = personalWorkspaceBuilder.organization;

    }

    public static class PersonalWorkspaceBuilder extends AbstractWorkspace.ComplexBuilder<PersonalWorkspaceBuilder, PersonalWorkspace> {

        private String email;
        private String organization;

        public PersonalWorkspaceBuilder() {
            this.type = WorkspaceType.PERSONAL;
        }

        public PersonalWorkspaceBuilder withEmail(String email) {
            this.email = email;
            return getThis();
        }

        public PersonalWorkspaceBuilder withOrganization(String organization) {
            this.organization = organization;
            return getThis();
        }

        @Override
        public PersonalWorkspace build() {
            return new PersonalWorkspace(this);
        }
    }
}
