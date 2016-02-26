/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.builders;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public abstract class InstanceExpiration implements Describable<InstanceExpiration> {

    public Descriptor<InstanceExpiration> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public abstract static class InstanceExpirationDescriptor extends Descriptor<InstanceExpiration> {
    }

    public static final class AlwaysOn extends InstanceExpiration {

        @DataBoundConstructor
        public AlwaysOn() {
        }

        @Extension
        public static class DescriptorImpl extends InstanceExpirationDescriptor {

            @Override
            public String getDisplayName() {
                return "Always on";
            }

        }
    }

}
