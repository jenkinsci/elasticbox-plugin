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

package com.elasticbox.jenkins.model.services.deployment.execution.context;


import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.DeploymentOrderRepository;
import com.elasticbox.jenkins.model.repository.InstanceRepository;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.util.TaskLogger;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import com.elasticbox.jenkins.model.services.deployment.execution.order.AbstractDeployBoxOrder;

import java.util.Set;

/**
 * Created by serna on 1/19/16.
 */
public abstract class AbstractBoxDeploymentContext<T extends AbstractDeployBoxOrder> {

    private ElasticBoxCloud cloud;
    private AbstractBuild<?, ?> build;
    private Launcher launcher;
    private BuildListener listener;
    private TaskLogger logger;

    private BoxRepository boxRepository;
    private InstanceRepository instanceRepository;
    private DeploymentOrderRepository deploymentOrderRepository;

    private String boxToDeployId;

    private DeploymentType deploymentType;

    protected T order;

    protected AbstractBoxDeploymentContext(AbstractBoxDeploymentContextBuilder builder) {

        this.logger = builder.logger;
        this.launcher = builder.launcher;
        this.build = builder.build;
        this.cloud = builder.cloud;
        this.listener = builder.listener;
        this.deploymentType = builder.deploymentType;

        //Default value, it would be modified later
        this.boxToDeployId = builder.box;
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

    public DeploymentType getDeploymentType() {
        return deploymentType;
    }

    public BoxRepository getBoxRepository() {
        return boxRepository;
    }

    public InstanceRepository getInstanceRepository() {
        return instanceRepository;
    }

    public DeploymentOrderRepository getDeploymentOrderRepository() {
        return deploymentOrderRepository;
    }

    public String getBoxToDeployId() {
        return boxToDeployId;
    }

    public void setBoxRepository(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

    public void setInstanceRepository(InstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    public void setDeploymentOrderRepository(DeploymentOrderRepository deploymentOrderRepository) {
        this.deploymentOrderRepository = deploymentOrderRepository;
    }

    public void setBoxToDeployId(String boxToDeployId) {
        this.boxToDeployId = boxToDeployId;
    }

    public static abstract class AbstractBoxDeploymentContextBuilder<B extends AbstractBoxDeploymentContextBuilder, T extends AbstractBoxDeploymentContext>{

        private ElasticBoxCloud cloud;
        private AbstractBuild<?, ?> build;
        private Launcher launcher;
        private BuildListener listener;
        private TaskLogger logger;
        private DeploymentType deploymentType;

        protected boolean waitForDone;
        protected String boxVersion;
        protected String box;
        protected String [] tags;
        protected String name;
        protected String owner;
        protected String expirationTime;
        protected String expirationOperation;

        abstract T build();

        protected B getThis() { return (B) this; }

        public B cloud(ElasticBoxCloud cloud){
            this.cloud = cloud;
            return getThis();
        }

        public B build(AbstractBuild<?, ?> build){
            this.build = build;
            return getThis();
        }

        public B launcher(Launcher launcher){
            this.launcher = launcher;
            return getThis();
        }

        public B listener(BuildListener listener){
            this.listener = listener;
            return getThis();
        }

        public B box(String box){
            this.box = box;
            return getThis();
        }

        public B boxVersion(String boxVersion){
            this.boxVersion = boxVersion;
            return getThis();
        }

        public B tags(Set<String> tags){
            this.tags = tags.toArray(new String[tags.size()]);
            return getThis();
        }

        public B name(String name){
            this.name = name;
            return getThis();
        }

        public B owner(String owner){
            this.owner = owner;
            return getThis();
        }

        public B expirationTime(String expirationTime){
            this.expirationTime = expirationTime;
            return getThis();
        }

        public B expirationOperation(String expirationOperation){
            this.expirationOperation = expirationOperation;
            return getThis();
        }

        public B waitForDone(boolean waitForDone){
            this.waitForDone = waitForDone;
            return getThis();
        }

        public B logger(TaskLogger logger){
            this.logger = logger;
            return getThis();
        }

        public B deploymentType(String deploymentTypeValue){
            this.deploymentType = DeploymentType.findBy(deploymentTypeValue);;
            return getThis();
        }

    }
}
