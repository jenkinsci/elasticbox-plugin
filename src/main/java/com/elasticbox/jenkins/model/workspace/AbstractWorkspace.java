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

import com.elasticbox.jenkins.model.AbstractModel;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;

public class AbstractWorkspace extends AbstractModel {


    public enum WorkspaceType {
        PERSONAL("/workspaces/personal"),
        TEAM("/workspaces/team");

        private String schema;
        WorkspaceType(String schema) {
            this.schema = schema;
        }

        public String getSchema() {
            return schema;
        }

        public static WorkspaceType findBy(String schema) {
            WorkspaceType[] values = WorkspaceType.values();
            for (WorkspaceType workspace : values) {
                if (schema.endsWith(workspace.getSchema())) {
                    return workspace;
                }
            }
            throw new ElasticBoxModelException("There is no workspace type whose schema ends with: " + schema);
        }
    }

    private WorkspaceType workSpaceType;
    private String schema;
    private String uri;
    private String name;

    public AbstractWorkspace(ComplexBuilder builder) {
        super(builder.id);
        this.workSpaceType = builder.type;
        this.schema = builder.schema;
        this.uri = builder.uri;
        this.name = builder.name;
    }

    public WorkspaceType getWorkSpaceType() {
        return workSpaceType;
    }

    public String getSchema() {
        return schema;
    }

    public String getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public interface Builder<T> {
        T build();
    }

    public abstract static class ComplexBuilder<B extends ComplexBuilder<B,T>,T> implements Builder<T> {

        protected WorkspaceType type;
        private String name;
        private String schema;
        private String uri;
        private String id;

        public B withId(String id) {
            this.id = id;
            return getThis();
        }

        public B withName(String name) {
            this.name = name;
            return getThis();
        }

        public B withType(WorkspaceType type) {
            this.type =  type;
            return getThis();
        }

        public B withSchema(String schema) {
            this.schema =  schema;
            return getThis();
        }

        public B withUri(String uri) {
            this.uri =  uri;
            return getThis();
        }

        @SuppressWarnings("unchecked")
        protected B getThis() {
            return (B) this;
        }

    }


}
