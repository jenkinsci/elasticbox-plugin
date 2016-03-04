package com.elasticbox.jenkins.builders.github;

import com.elasticbox.jenkins.triggers.PullRequestBuildTrigger;
import com.elasticbox.jenkins.triggers.github.TriggerCause;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.text.MessageFormat;

public class PullRequestCommenter extends Builder {
    private final String comment;

    @DataBoundConstructor
    public PullRequestCommenter(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        if (StringUtils.isBlank(comment)) {
            return true;
        }

        TriggerCause cause = build.getCause(TriggerCause.class);
        if (cause == null) {
            throw new AbortException(MessageFormat.format("{0} is not configured for this project",
                    Jenkins.getInstance().getDescriptor(PullRequestBuildTrigger.class).getDisplayName()));
        }

        TaskLogger logger = new TaskLogger(listener);
        String resolvedComment = new VariableResolver(build, listener).resolve(comment);
        logger.info("Posting the following comment to {0}: {1}", cause.getPullRequest().getHtmlUrl(), resolvedComment);
        cause.getPullRequest().comment(resolvedComment);

        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Comment on GitHub pull request";
        }

    }
}
