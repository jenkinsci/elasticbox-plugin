/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.triggers.github;

import com.elasticbox.jenkins.util.ProjectData;
import hudson.model.AbstractProject;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.kohsuke.github.GHPullRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestData {

    public final URL pullRequestUrl;
    private Date lastUpdated;
    private String headSha;
    private final List<PullRequestInstance> instances;

    private transient ProjectData projectData;

    public PullRequestData(GHPullRequest pullRequest, ProjectData projectData) throws IOException {
        this.pullRequestUrl = pullRequest.getHtmlUrl();
        this.headSha = pullRequest.getHead().getSha();
        this.lastUpdated = pullRequest.getUpdatedAt();
        this.instances = new ArrayList<PullRequestInstance>();
        this.projectData = projectData;
    }

    public AbstractProject getProject() {
        return projectData.getProject();
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public String getHeadSha() {
        return headSha;
    }

    public List<PullRequestInstance> getInstances() {
        return instances;
    }

    public boolean update(GHPullRequest pullRequest) throws IOException {
        if (!pullRequest.getHtmlUrl().equals(pullRequestUrl)) {
            return false;
        }
        if (pullRequest.getUpdatedAt().compareTo(lastUpdated) <= 0) {
            return false;
        }
        boolean updated = !pullRequest.getHead().getSha().equals(headSha);
        lastUpdated = pullRequest.getUpdatedAt();
        headSha = pullRequest.getHead().getSha();
        return updated;
    }

    protected void setProjectData(ProjectData projectData) {
        this.projectData = projectData;
    }

    public synchronized void save() throws IOException {
        PullRequests pullRequests = projectData.get(PullRequests.class);
        if (pullRequests == null) {
            projectData.add(new PullRequests());
            pullRequests = projectData.get(PullRequests.class);
        }
        List<PullRequestData> dataList = pullRequests.getData();
        if (dataList == null) {
            projectData.add(new PullRequests());
        }
        if (!dataList.contains(this)) {
            dataList.add(this);
        }
        projectData.save();
    }

    public void remove() throws IOException {
        projectData.get(PullRequests.class).getData().remove(this);
        projectData.save();
    }

}
