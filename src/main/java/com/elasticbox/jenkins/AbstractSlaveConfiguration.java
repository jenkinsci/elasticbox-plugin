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

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.util.Set;
import jenkins.model.Jenkins;

/**
 *
 * @author Phong Nguyen Le
 */
public abstract class AbstractSlaveConfiguration implements Describable<AbstractSlaveConfiguration> {
    private String id;
    private final String workspace;
    private final String box;
    private final String profile;
    private final String variables;
    private final String boxVersion;
    private final int minInstances;
    private final int maxInstances;
    private String environment;
    private final String labels;
    private final String remoteFS;
    private final String description;
    private final Node.Mode mode;
    private final int retentionTime;
    private int executors;
    private final int launchTimeout;
    
    private transient Set<LabelAtom> labelSet;

    public AbstractSlaveConfiguration(String id, String workspace, String box, String boxVersion, String profile, int minInstances,
            int maxInstances, String environment, String variables, String labels, String description, String remoteFS, 
            Node.Mode mode, int retentionTime, int executors, int launchTimeout) {
        super();
        this.id = id;
        this.workspace = workspace;
        this.box = box;
        this.boxVersion = boxVersion;
        this.profile = profile;
        this.minInstances = minInstances;
        this.maxInstances = maxInstances;
        this.environment = environment;
        this.variables = variables;    
        this.labels = labels;
        this.description = description;
        this.remoteFS = remoteFS;
        this.mode = mode;
        this.retentionTime = retentionTime;
        this.executors = executors;
        this.launchTimeout = launchTimeout;
        
        this.labelSet = getLabelSet();
    }    
    
    @Override
    public Descriptor<AbstractSlaveConfiguration> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
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

    public String getVariables() {
        return variables;
    }

    public String getBoxVersion() {
        return boxVersion;
    }

    public String getEnvironment() {
        return environment;
    }

    protected void setEnvironment(String environment) {
        this.environment = environment;
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

    public int getLaunchTimeout() {
        return launchTimeout;
    }

    public Node.Mode getMode() {
        return mode;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public final Set<LabelAtom> getLabelSet() {
        if (labelSet == null) {
            labelSet = Label.parse(labels);
        }
        
        return labelSet;
    }

}
