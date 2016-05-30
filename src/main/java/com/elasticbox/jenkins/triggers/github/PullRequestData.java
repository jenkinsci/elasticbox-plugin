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
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class PullRequestData {
    private static final Logger LOGGER = Logger.getLogger(PullRequestData.class.getName());

    public final String pullRequestUrl;
    private Date lastUpdated;
    private String headSha;
    private final List<PullRequestInstance> instances;

    private transient ProjectData projectData;

    public PullRequestData(GHPullRequest pullRequest, ProjectData projectData) throws IOException {
        this.pullRequestUrl = pullRequest.getHtmlUrl().toExternalForm();
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

        LOGGER.info("Existing Pull Request data before updating: " + toString() );

        // Comparing Strings, since URL objects may differ even for the same URL string because of any other field
        if (!pullRequest.getHtmlUrl().toExternalForm().equals(pullRequestUrl) ) {
            LOGGER.warning("Pull Request URLs do not match: " + pullRequestUrl + " != " + pullRequest.getHtmlUrl() );
            return false;
        }
        if (pullRequest.getUpdatedAt().compareTo(lastUpdated) <= 0) {
            return false;
        }

        lastUpdated = pullRequest.getUpdatedAt();
        final String newSha = pullRequest.getHead().getSha();
        boolean updated = !newSha.equals(headSha);
        headSha = newSha;

        LOGGER.info("Updated Pull Request data: " + toString() );

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

    @Override
    public String toString() {
        return "PullRequestData{Url=" + pullRequestUrl
                + ", lastUpdated=" + lastUpdated
                + ", instances=" + ( (instances.size() > 0) ? StringUtils.join(instances, ';') : "NONE")
                + ", headSha=" + headSha + '}';
    }
}
