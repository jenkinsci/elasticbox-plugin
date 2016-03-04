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
import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.builders.InstanceExpiration;
import com.elasticbox.jenkins.builders.InstanceExpirationSchedule;
import com.elasticbox.jenkins.model.services.deployment.execution.order.ApplicationBoxDeploymentOrder;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.AbortException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.text.ParseException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApplicationBoxDeploymentContext extends AbstractBoxDeploymentContext<ApplicationBoxDeploymentOrder> {

    private ApplicationBoxDeploymentContext(Builder builder) {
        super(builder);
        this.order = new ApplicationBoxDeploymentOrder(
                builder.waitForDone,
                builder.box,
                builder.boxVersion,
                builder.tags,
                builder.name,
                builder.owner,
                builder.expirationTime,
                builder.expirationOperation,
                builder.requirements);
    }


    public static class Builder extends AbstractBoxDeploymentContextBuilder<Builder, ApplicationBoxDeploymentContext> {

        private String[] requirements;

        public Builder requirements(String [] requirements) {
            this.requirements = requirements;
            return this;
        }

        @Override
        public ApplicationBoxDeploymentContext build() {
            return new ApplicationBoxDeploymentContext(this);
        }
    }

    public static class ApplicationBoxDeploymentFactory
            extends DeploymentContextFactory<ApplicationBoxDeploymentContext> {

        @Override
        public ApplicationBoxDeploymentContext createContext(DeployBox deployBox,
                                                             VariableResolver variableResolver,
                                                             ElasticBoxCloud cloud,
                                                             AbstractBuild<?, ?> build,
                                                             Launcher launcher,
                                                             BuildListener listener,
                                                             TaskLogger logger) throws AbortException {


            Set<String> resolvedTags = variableResolver.resolveTags(deployBox.getTags());

            String expirationTime = null;
            String expirationOperation = null;
            final InstanceExpiration expiration = deployBox.getExpiration();
            if (expiration instanceof InstanceExpirationSchedule) {
                InstanceExpirationSchedule expirationSchedule = (InstanceExpirationSchedule) expiration;
                try {
                    expirationTime = expirationSchedule.getUtcDateTime();
                } catch (ParseException ex) {
                    Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                    logger.error("Error parsing expiration time: {0}", ex.getMessage());
                    throw new AbortException(ex.getMessage());
                }
                expirationOperation = expirationSchedule.getOperation();

            }

            return new ApplicationBoxDeploymentContext.Builder()
                    .logger(logger)
                    .cloud(cloud)
                    .build(build)
                    .launcher(launcher)
                    .listener(listener)
                    .box(deployBox.getBox())
                    .boxVersion(deployBox.getBoxVersion())
                    .name(deployBox.getInstanceName())
                    .requirements(commaSeparatedValuesToArray(deployBox.getClaims()))
                    .tags(resolvedTags)
                    .owner(deployBox.getWorkspace())
                    .expirationOperation(expirationOperation)
                    .expirationTime(expirationTime)
                    .deploymentType(deployBox.getBoxDeploymentType())
                    .waitForDone(deployBox.isWaitForCompletion())
                    .build();

        }

    }
}
