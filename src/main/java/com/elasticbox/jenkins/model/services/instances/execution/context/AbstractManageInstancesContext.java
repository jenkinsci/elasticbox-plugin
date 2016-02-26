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

package com.elasticbox.jenkins.model.services.instances.execution.context;


import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.InstanceRepository;
import com.elasticbox.jenkins.model.services.instances.execution.order.ManageInstanceOrder;
import com.elasticbox.jenkins.util.TaskLogger;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.util.Set;

public abstract class AbstractManageInstancesContext<T extends ManageInstanceOrder> {

    private ElasticBoxCloud cloud;
    private AbstractBuild<?, ?> build;
    private Launcher launcher;
    private BuildListener listener;
    private TaskLogger logger;

    private BoxRepository boxRepository;
    private InstanceRepository instanceRepository;


    protected T order;

    protected AbstractManageInstancesContext(ManageInstancesContextBuilder builder) {

        this.logger = builder.logger;
        this.launcher = builder.launcher;
        this.build = builder.build;
        this.cloud = builder.cloud;
        this.listener = builder.listener;
    }

    public T getOrder() {
        return order;
    }

    public ElasticBoxCloud getCloud() {
        return cloud;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public BuildListener getListener() {
        return listener;
    }

    public TaskLogger getLogger() {
        return logger;
    }

    public BoxRepository getBoxRepository() {
        return boxRepository;
    }

    public InstanceRepository getInstanceRepository() {
        return instanceRepository;
    }

    public void setBoxRepository(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

    public void setInstanceRepository(InstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    public abstract static class ManageInstancesContextBuilder
            <B extends ManageInstancesContextBuilder, T extends AbstractManageInstancesContext> {

        private ElasticBoxCloud cloud;
        private AbstractBuild<?, ?> build;
        private Launcher launcher;
        private BuildListener listener;
        private TaskLogger logger;

        protected boolean waitForDone;
        protected String boxVersion;
        protected String box;
        protected String [] tags;
        protected String [] variables;

        abstract T build();

        public B build(AbstractBuild<?, ?> build) {
            this.build = build;
            return getThis();
        }

        protected B getThis() {
            return (B) this;
        }

        public B cloud(ElasticBoxCloud cloud) {
            this.cloud = cloud;
            return getThis();
        }

        public B launcher(Launcher launcher) {
            this.launcher = launcher;
            return getThis();
        }

        public B listener(BuildListener listener) {
            this.listener = listener;
            return getThis();
        }

        public B box(String box) {
            this.box = box;
            return getThis();
        }

        public B boxVersion(String boxVersion) {
            this.boxVersion = boxVersion;
            return getThis();
        }

        public B tags(Set<String> tags) {
            this.tags = tags.toArray(new String[tags.size()]);
            return getThis();
        }

        public B waitForDone(boolean waitForDone) {
            this.waitForDone = waitForDone;
            return getThis();
        }

        public B logger(TaskLogger logger) {
            this.logger = logger;
            return getThis();
        }

    }
}
