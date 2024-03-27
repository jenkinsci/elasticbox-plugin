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

import hudson.model.Cause;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.text.MessageFormat;

public class TriggerCause extends Cause {

    private final String shortDescription;

    private final transient GHPullRequest pullRequest;

    public TriggerCause(GHEventPayload.PullRequest prEventPayload) throws IOException {
        this (prEventPayload.getPullRequest(),
            MessageFormat.format("GitHub pull request {0} is {1} by {2}",
                prEventPayload.getPullRequest().getHtmlUrl(),
                PullRequestManager.PullRequestAction.SYNCHRONIZE.equals(prEventPayload.getAction())
                    ? "updated"
                    : prEventPayload.getAction(),
                getUserInfo(prEventPayload.getPullRequest().getUser())));
    }

    public TriggerCause(GHPullRequest pullRequest, GHUser buildRequester) throws IOException {
        this(pullRequest, MessageFormat.format("Build for pull request {0} is started by {1}", pullRequest.getHtmlUrl(),
                getUserInfo(buildRequester)));
    }

    public TriggerCause(GHPullRequest pullRequest, String shortDescription) {
        this.pullRequest = pullRequest;
        this.shortDescription = shortDescription;
    }

    public GHPullRequest getPullRequest() {
        return pullRequest;
    }

    @Override
    public String getShortDescription() {
        return shortDescription;
    }

    private static String getUserInfo(GHUser user) throws IOException {
        return StringUtils.isBlank(user.getName()) || user.getLogin().equals(user.getName()) ? user.getLogin() :
                MessageFormat.format("{0} ({1})", user.getName(), user.getLogin());
    }

}
