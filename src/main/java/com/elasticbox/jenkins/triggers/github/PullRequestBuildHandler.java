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

import com.cloudbees.jenkins.GitHubRepositoryName;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.elasticbox.jenkins.ElasticBoxExecutor;
import com.elasticbox.jenkins.triggers.IBuildHandler;
import com.elasticbox.jenkins.triggers.PullRequestBuildTrigger;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Actionable;
import hudson.model.Executor;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.RevisionParameterAction;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.BuildData;
import hudson.security.ACL;
import hudson.util.SequentialExecutionQueue;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class PullRequestBuildHandler implements IBuildHandler {

    private static final Logger LOGGER = Logger.getLogger(PullRequestBuildHandler.class.getName());

    public static final String PR_NUMBER = "PR_NUMBER";
    public static final String PR_BRANCH = "PR_BRANCH";
    public static final String BUILD_REQUESTER = "BUILD_REQUESTER";
    public static final String BUILD_REQUEST_EMAIL = "BUILD_REQUEST_EMAIL";
    public static final String PR_COMMIT = "PR_COMMIT";
    public static final String PR_MERGE_BRANCH = "PR_MERGE_BRANCH";
    public static final String PR_OWNER = "PR_OWNER";
    public static final String PR_OWNER_EMAIL = "PR_OWNER_EMAIL";
    public static final String PR_URL = "PR_URL";
    public static final String PR_TITLE = "PR_TITLE";

    private static final SequentialExecutionQueue sequentialExecutionQueue
        = new SequentialExecutionQueue(ElasticBoxExecutor.threadPool);

    private static final Collection<GHEvent> WEBHOOK_EVENTS
        = Arrays.asList(GHEvent.PULL_REQUEST, GHEvent.ISSUE_COMMENT);

    private final AbstractProject<?, ?> project;
    private final Pattern triggerPhrasePattern;
    private String gitHubRepositoryUrl;
    private Set<String> whitelist;

    public PullRequestBuildHandler(AbstractProject<?, ?> project, boolean newTrigger) throws IOException {
        this.project = project;

        final GithubProjectProperty property = project.getProperty(GithubProjectProperty.class);

        if (property == null
            || property.getProjectUrl() == null
            || StringUtils.isBlank(property.getProjectUrl().baseUrl())) {

            throw new IOException(
                MessageFormat.format("GitHub project URL must be specified for project {0}", project.getFullName()));
        }

        String gitHubProjectUrl = property.getProjectUrl().baseUrl().trim();
        GitHubRepositoryName gitHubRepoName = GitHubRepositoryName.create(gitHubProjectUrl);
        if (gitHubRepoName == null) {
            throw new IOException(MessageFormat.format("Invalid GitHub project URL specified: {0}", gitHubProjectUrl));
        }

        GHRepository repo = gitHubRepoName.resolveOne();
        if (repo == null) {
            LOGGER.severe(
                MessageFormat.format("Cannot connect to {0}. Please check your registered GitHub credentials",
                    gitHubRepoName));

            gitHubRepositoryUrl = gitHubProjectUrl;
        } else {
            gitHubRepositoryUrl = repo.getHtmlUrl().toString();
        }
        if (!gitHubRepositoryUrl.endsWith("/")) {
            gitHubRepositoryUrl += '/';
        }

        PullRequestBuildTrigger trigger = project.getTrigger(PullRequestBuildTrigger.class);
        triggerPhrasePattern = Pattern.compile(trigger.getTriggerPhrase(), Pattern.CASE_INSENSITIVE);
        if (StringUtils.isNotBlank(trigger.getWhitelist())) {
            Set<String> entries = new HashSet<String>();
            for (String entry : trigger.getWhitelist().split(",")) {
                if (StringUtils.isNotBlank(entry)) {
                    entries.add(entry);
                }
            }
            if (!entries.isEmpty()) {
                whitelist = entries;
            }
        }

        if (newTrigger) {
            configureGit(gitHubRepoName, trigger);

            // The construction of webhook URL requires Jenkins root URL which might be null if it is not manually
            // configured and it is retrieved in a separate thread without request context. So the webhook URL is first
            // retrieved here.
            PullRequestBuildTrigger.DescriptorImpl descriptor
                = (PullRequestBuildTrigger.DescriptorImpl) Jenkins.getInstance()
                                                                    .getDescriptor(PullRequestBuildTrigger.class);

            final String webhookUrl = StringUtils.isBlank(descriptor.getWebHookExternalUrl())
                ? descriptor.getWebHookUrl()
                : descriptor.getWebHookExternalUrl();

            if (webhookUrl == null) {
                LOGGER.warning(
                    MessageFormat.format(
                        "Cannot add webhook to GitHub repository {0}. "
                            + "Please configure Jenkins URL or Webhook External URL for {1}",
                        gitHubRepositoryUrl,
                        descriptor.getDisplayName()));

            } else {
                LOGGER.info(
                    MessageFormat.format("Adding webhook {0} to GitHub repository {1}",
                        webhookUrl,
                        gitHubRepositoryUrl));

                sequentialExecutionQueue.execute(new Runnable() {

                    public void run() {
                        try {
                            createWebHook(webhookUrl);
                        } catch (Throwable ex) {
                            LOGGER.log(
                                Level.SEVERE,
                                MessageFormat.format("Error adding webhook to GitHub repository {0}",
                                    gitHubRepositoryUrl),
                                ex);
                        }
                    }
                });
            }
        }
    }

    public static String getPullRequestAsString(GHPullRequest pullRequest) throws IOException {
        return pullRequest.getHtmlUrl() + " [" + pullRequest.getState() + "] Updated: " + pullRequest.getUpdatedAt();
    }

    public String getGitHubRepositoryUrl() {
        return gitHubRepositoryUrl;
    }

    private void configureGit(GitHubRepositoryName gitHubRepoName, PullRequestBuildTrigger trigger) throws IOException {

        if (project.getScm() instanceof GitSCM) {
            GitSCM git = (GitSCM) project.getScm();
            List<UserRemoteConfig> userRemoteConfigs = new ArrayList<UserRemoteConfig>();

            if (git.getUserRemoteConfigs().isEmpty()
                || (git.getUserRemoteConfigs().size() == 1
                    && StringUtils.isBlank(git.getUserRemoteConfigs().get(0).getUrl()))) {

                LOGGER.info(
                    MessageFormat.format(
                        "Git is selected as SCM of project {0} but not yet configured, configuring it",
                        project.getFullName()));

                String url = MessageFormat.format(
                    "https://{0}/{1}/{2}.git",
                    gitHubRepoName.getHost(),
                    gitHubRepoName.getUserName(),
                    gitHubRepoName.getRepositoryName());

                userRemoteConfigs.add(
                    new UserRemoteConfig(url, "origin", "+refs/pull/*:refs/remotes/origin/pr/*", null));

                List<BranchSpec> branches = new ArrayList<BranchSpec>();

                branches.add(new BranchSpec("${PR_COMMIT}"));
                GitSCM updatedGit = new GitSCM(userRemoteConfigs, branches, git.isDoGenerateSubmoduleConfigurations(),
                        git.getSubmoduleCfg(), git.getBrowser(), git.getGitTool(), git.getExtensions());

                project.setScm(updatedGit);

                LOGGER.info(
                    MessageFormat.format(
                        "Git is configured to work with {0}",
                        trigger.getDescriptor().getDisplayName()));
            }
        }
    }

    private GHHook createWebHook(String webhookUrl) throws IOException {
        final GitHubRepositoryName gitHubRepoName = GitHubRepositoryName.create(gitHubRepositoryUrl);
        for (GHRepository repo : gitHubRepoName.resolve()) {
            // check if the webhook already exists
            for (GHHook hook : repo.getHooks()) {
                if ("web".equals(hook.getName()) && webhookUrl.equals(hook.getConfig().get("url"))) {
                    LOGGER.info(
                        MessageFormat.format("Webhook {0} already exists for {1}", webhookUrl, gitHubRepositoryUrl));
                    return hook;
                }
            }
            try {
                Map<String, String> config = new HashMap<String, String>();
                config.put("url", webhookUrl);
                config.put("insecure_ssl", "1");
                GHHook hook = repo.createHook("web", config, WEBHOOK_EVENTS, true);
                LOGGER.info(
                    MessageFormat.format(
                        "Webhook {0} is added to GitHub repository {1}",
                        webhookUrl,
                        gitHubRepositoryUrl));

                return hook;
            } catch (IOException e) {
                LOGGER.log(
                    Level.FINEST,
                    MessageFormat.format("Failed to add webhook {0} to GitHub repository {1}",
                        webhookUrl,
                        gitHubRepositoryUrl),
                    e);
            }
        }
        LOGGER.warning(
            MessageFormat.format(
                "Cannot add webhook {0} to GitHub repository {1}. "
                    + "Make sure that you specified a valid GitHub credential for GitHub project {1} in Jenkins "
                    + "configuration.",
                webhookUrl,
                gitHubRepositoryUrl));

        return null;
    }

    void handle(GHEventPayload.PullRequest prEventPayload, GitHub gitHub) throws IOException {
        GHPullRequest pullRequest = prEventPayload.getPullRequest();
        String pullRequestUrl = pullRequest.getHtmlUrl().toString();
        if (!pullRequestUrl.startsWith(gitHubRepositoryUrl)) {
            LOGGER.config(
                MessageFormat.format(
                    "Pull request {0} is not related to project {1}. "
                        + "GitHub project URL configured for project {1}: {2}",
                    pullRequestUrl,
                    project.getFullName(),
                    gitHubRepositoryUrl));
            return;
        }

        LOGGER.info(
            MessageFormat.format("Handling event ''{0}'' of pull request {1} for project {2}",
                prEventPayload.getAction(),
                pullRequestUrl,
                project.getFullName()));

        PullRequestManager pullRequestManager = PullRequestManager.getInstance();
        PullRequestData pullRequestData = pullRequestManager.getPullRequestData(pullRequestUrl, project);

        if (PullRequestManager.PullRequestAction.CLOSED.equals(prEventPayload.getAction())) {
            if (pullRequestData != null) {
                cancelBuilds(pullRequestData);
                deleteInstances(pullRequest);
            } else {
                LOGGER.warning("No previous data available for received Pull Request 'close' event: "
                        + pullRequestUrl);
            }
            return;
        }

        if (!isWhitelisted(pullRequest.getUser(), gitHub)) {
            LOGGER.info(
                MessageFormat.format(
                    "GitHub user {0} is not in the whitelist of project {1}",
                    pullRequest.getUser().getLogin(),
                    project.getFullName()));

            return;
        }

        boolean startBuild = false;

        if (pullRequestData == null) {
            if (PullRequestManager.PullRequestAction.SYNCHRONIZE.equals(prEventPayload.getAction())) {
                LOGGER.info(MessageFormat.format("Updated pull request {0} was not built previously", pullRequestUrl));
            }
            pullRequestData = pullRequestManager.addPullRequestData(pullRequest, project);
            startBuild = pullRequestData.getLastUpdated().equals(pullRequest.getUpdatedAt());

        } else if (pullRequestData.update(pullRequest)) {
            pullRequestData.save();
            startBuild = true;
        }

        if (LOGGER.isLoggable(Level.FINEST) ) {
            LOGGER.finest("Received event payload: " + prEventPayload);
        }

        if (startBuild) {
            if (LOGGER.isLoggable(Level.FINE) ) {
                LOGGER.fine("Cancelling previous running builds and starting new build for Pull request: "
                        + pullRequestData);
            }
            cancelBuilds(pullRequestData);
            build(pullRequest, null, new TriggerCause(prEventPayload));

        } else if (LOGGER.isLoggable(Level.FINE) ) {
            LOGGER.fine("No new build has been triggered for Pull request: " + pullRequestData);
        }
    }

    void handle(GHEventPayload.IssueComment issueComment, GitHub gitHub) throws IOException {
        // check the trigger phrase
        PullRequestBuildTrigger trigger = project.getTrigger(PullRequestBuildTrigger.class);
        if (StringUtils.isBlank(trigger.getTriggerPhrase() )) {
            if (LOGGER.isLoggable(Level.FINE) ) {
                LOGGER.fine("No trigger phrase configured for project: " + project.getDisplayName() );
            }
            return;
        }

        String issueUrl = issueComment.getIssue().getHtmlUrl().toString();
        if (!issueUrl.startsWith(gitHubRepositoryUrl)) {
            LOGGER.finest(
                MessageFormat.format(
                    "GitHub issue {0} is not related to project {1}. "
                        + "GitHub project URL configured for project {1}: {2}",
                    issueUrl,
                    project.getFullName(),
                    gitHubRepositoryUrl));

            return;
        }

        String commentBody = issueComment.getComment().getBody();
        if (!triggerPhrasePattern.matcher(commentBody).find()) {
            if (LOGGER.isLoggable(Level.FINE) ) {
                LOGGER.fine("No trigger phrase matching on comment: " + commentBody );
            }
            return;
        }

        GHUser buildRequester = issueComment.getComment().getUser();
        if (!isWhitelisted(buildRequester, gitHub)) {
            LOGGER.info(
                MessageFormat.format(
                    "GitHub user {0} is not in the whitelist of project {1}",
                    buildRequester.getLogin(),
                    project.getFullName()));

            return;
        }

        final int prNumber = issueComment.getIssue().getNumber();

        GHPullRequest pullRequest = issueComment.getRepository().getPullRequest(prNumber);
        // Updating PR to force the cache (if present) to refresh:
        pullRequest.setTitle(pullRequest.getTitle() );

        pullRequest = issueComment.getRepository().getPullRequest(prNumber);

        if (LOGGER.isLoggable(Level.FINE) ) {
            LOGGER.fine("ghPullRequest = " + getPullRequestAsString(pullRequest) );
        }

        if (pullRequest.getState() == GHIssueState.OPEN) {
            PullRequestData pullRequestData = PullRequestManager.getInstance().addPullRequestData(pullRequest, project);

            cancelBuilds(pullRequestData);
            build(pullRequest, buildRequester, new TriggerCause(pullRequest, buildRequester));
        } else if (LOGGER.isLoggable(Level.FINE) ) {
            LOGGER.fine(MessageFormat.format(
                    "Pull request {0} is not opened, no build is triggered", pullRequest.getHtmlUrl().toString() ));
        }
    }

    private boolean isWhitelisted(GHUser user, GitHub gitHub) throws IOException {
        if (whitelist == null) {
            return true;
        }

        if (whitelist.contains(user.getLogin())) {
            return true;
        }

        Set<String> whitelistOrgNames = gitHub.getMyOrganizations().keySet();
        whitelistOrgNames.retainAll(whitelist);
        for (String name : whitelistOrgNames) {
            GHOrganization organization = gitHub.getOrganization(name);
            if (organization.hasMember(user)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPullRequestBuild(Actionable actionable, String pullRequestUrl) {
        ParametersAction parameters = actionable.getAction(ParametersAction.class);

        if (parameters == null) {
            return false;
        }

        final ParameterValue pr_url = parameters.getParameter("PR_URL");
        if (pr_url == null) {
            return false;
        }

        return pullRequestUrl.equals(pr_url.getValue() );
    }

    void cancelBuilds(PullRequestData pullRequestData) {
        final String pullRequestUrl = pullRequestData.pullRequestUrl.toString();
        List<AbstractBuild> pullRequestBuilds = new ArrayList<AbstractBuild>();
        LOGGER.info("Checking if there is any previous build on queue for Pull Request: " + pullRequestUrl);

        for (Object b : project.getBuilds()) {
            LOGGER.info("Checking current builds: " + b);
            if (b instanceof AbstractBuild) {
                AbstractBuild build = (AbstractBuild) b;
                LOGGER.info("Como b es una instancia de AbstractBuild, miro a ver");

                if (build.isBuilding() && isPullRequestBuild(build, pullRequestUrl)) {
                    pullRequestBuilds.add(build);
                }
            }
        }
        if (!pullRequestBuilds.isEmpty()) {
            String[] buildNumbers = new String[pullRequestBuilds.size()];
            for (int i = 0; i < pullRequestBuilds.size(); i++) {
                buildNumbers[i] = String.valueOf(pullRequestBuilds.get(i).getNumber());
            }
            LOGGER.info(MessageFormat.format("Aborting the following builds of pull request {0}: {1}",
                    pullRequestUrl, StringUtils.join(buildNumbers, ", ")));
            for (AbstractBuild build : pullRequestBuilds) {
                Executor executor = build.getExecutor();
                if (executor != null) {
                    executor.interrupt();
                }
            }
        } else {
            LOGGER.fine("There is no previous running builds to cancel for Pull Request: " + pullRequestUrl);
        }
        LOGGER.info("Checking if there is any build on queue for Pull Request: " + pullRequestUrl);
        for (Queue.Item item: Jenkins.getInstance().getQueue().getUnblockedItems() ) {
            if (isPullRequestBuild(item, pullRequestUrl) ) {
                LOGGER.info(MessageFormat.format("Cancelling item id {0} found in queue matching Pull Request: {1}",
                        item.getId(), pullRequestUrl));
                Jenkins.getInstance().getQueue().cancel(item);
            }
        }
    }

    private ArrayList<ParameterValue> getDefaultBuildParameters() {
        ArrayList<ParameterValue> values = new ArrayList<ParameterValue>();
        ParametersDefinitionProperty property = project.getProperty(ParametersDefinitionProperty.class);
        if (property != null) {
            for (ParameterDefinition pd : property.getParameterDefinitions()) {
                values.add(pd.getDefaultParameterValue());
            }
        }
        return values;
    }

    private void build(GHPullRequest pr, GHUser buildRequester, TriggerCause cause) throws IOException {
        ArrayList<ParameterValue> parameters = getDefaultBuildParameters();
        parameters.add(new StringParameterValue(PR_COMMIT, pr.getHead().getSha()));
        parameters.add(new StringParameterValue(PR_BRANCH, pr.getHead().getRef()));
        if (buildRequester != null) {
            parameters.add(new StringParameterValue(BUILD_REQUESTER, buildRequester.getLogin()));
            if (buildRequester.getEmail() != null) {
                parameters.add(new StringParameterValue(BUILD_REQUEST_EMAIL, buildRequester.getEmail()));
            }
        }
        parameters.add(new StringParameterValue(PR_NUMBER, String.valueOf(pr.getNumber())));
        parameters.add(new StringParameterValue(PR_MERGE_BRANCH, pr.getBase().getRef()));
        parameters.add(new StringParameterValue(PR_OWNER, pr.getUser().getLogin()));
        if (pr.getUser().getEmail() != null) {
            parameters.add(new StringParameterValue(PR_OWNER_EMAIL, pr.getUser().getEmail()));
        }
        final StringParameterValue prUrlParam = new StringParameterValue(PR_URL, pr.getHtmlUrl().toString());
        parameters.add(prUrlParam);
        parameters.add(new StringParameterValue(PR_TITLE, pr.getTitle()));

        project.scheduleBuild2(project.getQuietPeriod(), cause, new ParametersAction(parameters),
                getBuildData(prUrlParam), new RevisionParameterAction(pr.getHead().getSha()));
    }

    private BuildData getBuildData(StringParameterValue pullRequestUrlParam) {
        for (Run<?, ?> build : project.getBuilds()) {

            if (isPullRequestBuild(build, String.valueOf(pullRequestUrlParam.getValue() ))) {

                List<BuildData> buildDataList = build.getActions(BuildData.class);
                if (!buildDataList.isEmpty()) {
                    return buildDataList.get(0);
                }
            }
        }
        return null;
    }

    private void deleteInstances(GHPullRequest pullRequest) throws IOException {
        PullRequestManager pullRequestManager = PullRequestManager.getInstance();
        List<PullRequestData> pullRequestDataList = new ArrayList<PullRequestData>();
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            for (AbstractProject<?,?> project : Jenkins.getInstance().getAllItems(AbstractProject.class)) {

                PullRequestData data =
                    pullRequestManager.removePullRequestData(pullRequest.getHtmlUrl().toString(), project);

                if (data != null) {
                    pullRequestDataList.add(data);
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
        PullRequestCleanup.deleteInstances(pullRequestDataList, pullRequest);
    }

}
