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

package com.elasticbox.jenkins;

import com.elasticbox.Client;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractSlaveConfiguration implements Describable<AbstractSlaveConfiguration> {
    private static final Logger LOGGER = Logger.getLogger(AbstractSlaveConfiguration.class.getName());

    private String id;
    private final String workspace;
    protected String boxDeploymentType;
    private final String box;
    private String profile;
    private final String claims;
    private final String provider;
    private final String location;
    private final String variables;
    private String boxVersion;
    private final int minInstances;
    private final int maxInstances;
    private String tags;
    private final String labels;
    private final String remoteFs;
    private final String description;
    private final Node.Mode mode;
    protected int retentionTime;
    private final int maxBuilds;
    private int executors;
    private final int launchTimeout;

    private transient Set<LabelAtom> labelSet;
    private transient String resolvedBoxVersion;
    private transient String resolvedDeploymentPolicy;

    public AbstractSlaveConfiguration(String id, String workspace, String box, String boxVersion, String profile,
            String claims, String provider, String location, int minInstances,
            int maxInstances, String tags, String variables, String labels, String description, String remoteFs,
            Node.Mode mode, int retentionTime, int maxBuilds, int executors, int launchTimeout,
                                      String boxDeploymentType) {
        super();
        this.id = id;
        this.workspace = workspace;
        this.box = box;
        this.boxVersion = boxVersion;
        this.profile = profile;
        this.claims = claims;
        this.provider = provider;
        this.location = location;
        this.minInstances = minInstances;
        this.maxInstances = maxInstances;
        this.tags = tags;
        this.variables = variables;
        this.labels = labels;
        this.description = description;
        this.remoteFs = remoteFs;
        this.mode = mode;
        this.retentionTime = retentionTime;
        this.maxBuilds = maxBuilds;
        this.executors = executors;
        this.launchTimeout = launchTimeout;
        this.boxDeploymentType =  boxDeploymentType;
        this.labelSet = getLabelSet();
    }

    protected Object readResolve() {
        if (boxVersion != null && boxVersion.equals(box)) {
            boxVersion = DescriptorHelper.LATEST_BOX_VERSION;
        }
        
        return this;
    }

    @Override
    public Descriptor<AbstractSlaveConfiguration> getDescriptor() {
        return Jenkins.get().getDescriptor(getClass());
    }

    public String getBoxDeploymentType() {
        return boxDeploymentType;
    }

    public String getId() {
        return id;
    }

    protected void setId(String id) {
        this.id = id;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }

    public String getProfile() {
        return profile;
    }

    void setProfile(String profile) {
        this.profile = profile;
    }

    public String getClaims() {
        return claims;
    }

    public String getProvider() {
        return provider;
    }

    public String getLocation() {
        return location;
    }

    public String getVariables() {
        return variables;
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public String getLabels() {
        return labels;
    }

    public int getMinInstances() {
        return minInstances;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public String getTags() {
        return tags;
    }

    public String getDescription() {
        return description;
    }

    public int getExecutors() {
        return executors;
    }

    protected void setExecutors(int executors) {
        this.executors = executors;
    }

    public int getRetentionTime() {
        return retentionTime;
    }

    public int getMaxBuilds() {
        return maxBuilds;
    }

    public String getMaxBuildsText() {
        return maxBuilds == 0 ? StringUtils.EMPTY : String.valueOf(maxBuilds);
    }

    public int getLaunchTimeout() {
        return launchTimeout;
    }

    public Node.Mode getMode() {
        return mode;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    public final Set<LabelAtom> getLabelSet() {
        if (labelSet == null) {
            labelSet = Label.parse(labels);
        }

        return labelSet;
    }

    public String getResolvedBoxVersion() {
        return resolvedBoxVersion;
    }

    public String getResolvedDeploymentPolicy() {
        return resolvedDeploymentPolicy;
    }

    String resolveBoxVersion(Client client) throws IOException {
        resolvedBoxVersion = DescriptorHelper.LATEST_BOX_VERSION.equals(boxVersion)
                ? client.getLatestBoxVersion(workspace, box) : boxVersion;
        return resolvedBoxVersion;
    }

    String resolveDeploymentPolicy(Client client) throws IOException {
        return resolvedDeploymentPolicy = DescriptorHelper.resolveDeploymentPolicy(client, workspace, profile, claims);
    }

    public abstract static class AbstractSlaveConfigurationDescriptor extends Descriptor<AbstractSlaveConfiguration> {

        public FormValidation doCheckMaxBuildsText(@QueryParameter String value) {
            if (StringUtils.isNotBlank(value)) {
                try {
                    int maxBuilds = Integer.parseInt(value);
                    if (maxBuilds < 1) {
                        return FormValidation.error("Enter a positive whole number.");
                    }
                } catch (NumberFormatException ex) {
                    return FormValidation.error("Invalid number, the number must be a positive whole number.");
                }
            }

            return FormValidation.ok();
        }

        protected FormValidation checkBoxVersion(String boxVersion, String box, String workspace, Client client) {
            if (StringUtils.isBlank(boxVersion)) {
                boxVersion = box;
            }
            if (DescriptorHelper.LATEST_BOX_VERSION.equals(boxVersion)) {
                try {
                    boxVersion = client.getLatestBoxVersion(workspace, box);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    return FormValidation.error(
                            MessageFormat.format("Error retrieving latest version of box {0}", box));
                }
            }
            return DescriptorHelper.checkSlaveBox(client, boxVersion);
        }

        public String uniqueId() {
            return UUID.randomUUID().toString();
        }

    }
}
