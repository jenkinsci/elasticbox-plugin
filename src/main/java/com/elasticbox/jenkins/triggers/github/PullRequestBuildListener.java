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

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.git.util.BuildData;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class PullRequestBuildListener extends RunListener<AbstractBuild<?, ?>> {
    private static final Logger LOGGER = Logger.getLogger(PullRequestBuildListener.class.getName());

    public boolean postStatus(
        AbstractBuild<?, ?> build, GHPullRequest pullRequest, GHCommitState status, String message) {

        String detailsUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();

        LOGGER.finest(
            MessageFormat.format(
                "Posting status {0} to {1} with details URL {2} and message: {3}",
                status,
                pullRequest.getHtmlUrl(),
                detailsUrl,
                message)
        );

        try {
            pullRequest.getRepository().createCommitStatus(pullRequest.getHead().getSha(), status, detailsUrl, message);
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error posting status to {0}", pullRequest.getHtmlUrl()), ex);
        }
        return false;
    }

    public void postComment(AbstractBuild<?, ?> build, GHPullRequest pullRequest, String comment) {
        String detailsUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        try {

            pullRequest.comment(MessageFormat.format("{0}. See {1} for more details.", comment, detailsUrl));

        } catch (IOException ex1) {

            LOGGER.log(
                Level.SEVERE,
                MessageFormat.format(
                    "Error posting comment to {0}",
                    pullRequest.getHtmlUrl()),
                ex1
            );
        }
    }

    @Override
    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        TriggerCause cause = build.getCause(TriggerCause.class);
        if (cause == null) {
            return;
        }
        GHPullRequest pullRequest = cause.getPullRequest();
        final String message = "Build STARTED";
        if (!postStatus(build, pullRequest, GHCommitState.PENDING, message)) {
            postComment(build, pullRequest, message);
        }
        try {
            String prLinkText = MessageFormat.format("PR #{0}", pullRequest.getNumber());
            build.setDescription(MessageFormat.format("<a title=''{0}'' href=''{1}''>{2}</a>: {3}",
                    pullRequest.getTitle(), pullRequest.getHtmlUrl(), prLinkText,
                    StringUtils.abbreviate(pullRequest.getTitle(), 58 - prLinkText.length())));
        } catch (IOException ex) {

            LOGGER.log(
                Level.SEVERE,
                MessageFormat.format(
                    "Error updating description of build {0}",
                    build.getUrl()),
                ex
            );
        }
    }

    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        TriggerCause cause = build.getCause(TriggerCause.class);
        if (cause == null) {
            return;
        }
        GHPullRequest pullRequest = cause.getPullRequest();
        // Remove previous build data of the pull request from the build
        for (Iterator<Action> iter = build.getActions().iterator(); iter.hasNext();) {
            Action action = iter.next();
            if (action instanceof BuildData) {
                BuildData buildData = (BuildData) action;

                if (buildData.getLastBuiltRevision() != null
                    && pullRequest.getHead().getSha().equals(buildData.getLastBuiltRevision())) {

                    iter.remove();
                    break;
                }
            }
        }
        GHCommitState status;
        if (build.getResult() == Result.SUCCESS) {
            status = GHCommitState.SUCCESS;
        } else if (build.getResult() == Result.FAILURE || build.getResult() == Result.NOT_BUILT) {
            status = GHCommitState.ERROR;
        } else {
            status = GHCommitState.FAILURE;
        }
        String message;
        if (build.getResult() == Result.ABORTED) {
            message = "Build was ABORTED";
        } else if (build.getResult() == Result.NOT_BUILT) {
            message = "Build was NOT PERFORMED";
        } else if (build.getResult() == Result.SUCCESS) {
            message = "Build finished SUCCESSFULLY";
        } else {
            message = "Build FAILED";
        }
        postComment(build, pullRequest, message);
        postStatus(build, pullRequest, status, message);
    }

}
