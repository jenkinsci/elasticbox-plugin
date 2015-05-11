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

import com.elasticbox.jenkins.triggers.PullRequestBuildTrigger;
import com.cloudbees.jenkins.Credential;
import com.cloudbees.jenkins.GitHubPushTrigger;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.builders.BuilderListener;
import com.elasticbox.jenkins.triggers.BuildManager;
import com.elasticbox.jenkins.util.ProjectData;
import com.elasticbox.jenkins.util.ProjectDataListener;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class PullRequestManager extends BuildManager<PullRequestBuildHandler> {
    private static final Logger LOGGER = Logger.getLogger(PullRequestManager.class.getName());

    public static interface PullRequestAction {
        public static final String OPENED = "opened";
        public static final String REOPENED = "reopened";
        public static final String SYNCHRONIZE = "synchronize";
        public static final String CLOSED = "closed";
    }
    
    private static final Set<String> SUPPORTED_EVENTS = new HashSet<String>(
            Arrays.asList(PullRequestAction.OPENED, PullRequestAction.REOPENED, PullRequestAction.SYNCHRONIZE, PullRequestAction.CLOSED));
    
    public static final PullRequestManager getInstance() {
        return (PullRequestManager) ((PullRequestBuildTrigger.DescriptorImpl) Jenkins.getInstance().getDescriptor(
                PullRequestBuildTrigger.class)).getBuildManager();        
    }
    
    final ConcurrentHashMap<AbstractProject, ConcurrentHashMap<String, PullRequestData>> projectPullRequestDataLookup = 
            new ConcurrentHashMap<AbstractProject, ConcurrentHashMap<String, PullRequestData>>();
    
    @Override
    public PullRequestBuildHandler createBuildHandler(AbstractProject<?, ?> project, boolean newTrigger) throws IOException {
        return new PullRequestBuildHandler(project, newTrigger);
    }
    
    public PullRequestData addPullRequestData(GHPullRequest pullRequest, AbstractProject project) throws IOException {
        ConcurrentHashMap<String, PullRequestData> pullRequestDataMap = projectPullRequestDataLookup.get(project);
        if (pullRequestDataMap == null) {
            projectPullRequestDataLookup.putIfAbsent(project, 
                    new ConcurrentHashMap<String, PullRequestData>());
            pullRequestDataMap = projectPullRequestDataLookup.get(project);
        }
        PullRequestData data = pullRequestDataMap.get(pullRequest.getHtmlUrl().toString());
        if (data == null) {
            String pullRequestUrl = pullRequest.getHtmlUrl().toString();
            PullRequestData newPullRequestData = new PullRequestData(pullRequest, ProjectData.getInstance(project, true));
            pullRequestDataMap.putIfAbsent(pullRequestUrl, newPullRequestData);
            data = pullRequestDataMap.get(pullRequestUrl);
            if (data == newPullRequestData) {
                data.save();
            }
        } 
        return data;
    }
    
    public PullRequestData getPullRequestData(String url, AbstractProject project) {
        ConcurrentHashMap<String, PullRequestData> pullRequestDataMap = projectPullRequestDataLookup.get(project);
        return pullRequestDataMap != null ? pullRequestDataMap.get(url) : null;
    }
    
    public PullRequestData removePullRequestData(String pullRequestUrl, AbstractProject project) throws IOException {
        ConcurrentHashMap<String, PullRequestData> pullRequestDataMap = projectPullRequestDataLookup.get(project);    
        PullRequestData pullRequestData = pullRequestDataMap != null ? pullRequestDataMap.remove(pullRequestUrl) : null;
        if (pullRequestData != null) {
            pullRequestData.remove();
        }
        return pullRequestData;
    }
    
    public GitHub createGitHub(GitHubRepositoryName gitHubRepoName) {
        GitHub gitHub = connect(gitHubRepoName);
        if (gitHub == null) {
            LOGGER.warning(MessageFormat.format("Cannot connect to {0}. Please check your registered GitHub credentials", gitHubRepoName));
        }
        return gitHub;        
    }
        
    private String getHost(Credential credential) {
        if (StringUtils.isNotBlank(credential.apiUrl)) {
            try {
                String host = new URL(credential.apiUrl).getHost();
                return "api.github.com".equals(host) ? "github.com" : host;
            } catch (MalformedURLException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Invalid GitHub API URL: {0}", credential.apiUrl), ex);
            }
        } 
        
        return "github.com";
    }
    
    private GitHub connect(Credential credential) {
        try {
            return credential.login();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("Error logging in GitHub at ''{0}'' with credential of user ''{1}''", getHost(credential), credential.username), ex);
            return null;
        }        
    }
    
    private GitHub connect(GitHubRepositoryName gitHubRepoName) {
        List<Credential> credentials = ((GitHubPushTrigger.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(GitHubPushTrigger.class)).getCredentials();
        List<Credential> hostMatchedCredentials = new ArrayList<Credential>();
        // try with the credential of the repository owner first
        for (Credential credential : credentials) {
            if (gitHubRepoName.host.equals(getHost(credential))) {
                if (gitHubRepoName.userName.equals(credential.username)) {
                    GitHub gitHub = connect(credential);
                    if (gitHub != null) {
                        return gitHub;
                    }
                } else {
                    hostMatchedCredentials.add(credential);
                }
            }
        }
        
        if (hostMatchedCredentials.isEmpty()) {
            LOGGER.warning(MessageFormat.format("Cannot find any credential for GitHub at {0}", gitHubRepoName.host));
        } else {        
            // try other credentials for the same host
            for (Credential credential : hostMatchedCredentials) {
                GitHub gitHub = connect(credential);
                if (gitHub != null) {
                    return gitHub;
                }
            }
        }
        
        return null;
    }
    
    private GitHub createGitHub(JSONObject payload) {
        String repoUrl = payload.getJSONObject("repository").getString("html_url");        
        return createGitHub(GitHubRepositoryName.create(repoUrl));
    }
    
    void handleEvent(String event, String payload) throws IOException {
        if ("pull_request".equals(event)) {
            handlePullRequestEvent(payload);
        } else if ("issue_comment".equals(event)) {
            handleIssueCommentEvent(payload);            
        } else {
            LOGGER.warning(MessageFormat.format("Unsupported GitHub event: ''{0}''", event));
        }
    }

    private void handlePullRequestEvent(String payload) throws IOException {
        GitHub gitHub = createGitHub(JSONObject.fromObject(payload));
        if (gitHub == null) {
            return;
        }
        GHEventPayload.PullRequest pullRequest = gitHub.parseEventPayload(new StringReader(payload), GHEventPayload.PullRequest.class);
        if (SUPPORTED_EVENTS.contains(pullRequest.getAction())) {
            Authentication old = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            try {
                for (AbstractProject<?,?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                    PullRequestBuildTrigger trigger = job.getTrigger(PullRequestBuildTrigger.class);
                    if (trigger != null && trigger.getBuildHandler() instanceof PullRequestBuildHandler) {
                        ((PullRequestBuildHandler) trigger.getBuildHandler()).handle(pullRequest, gitHub);
                    }
                }
            } finally {
                SecurityContextHolder.getContext().setAuthentication(old);
            }   
        } else {
            LOGGER.warning(MessageFormat.format("Unsupported pull request action: ''{0}''", pullRequest.getAction()));
        }
    }

    private void handleIssueCommentEvent(String payload) throws IOException {
        GitHub gitHub = createGitHub(JSONObject.fromObject(payload));
        if (gitHub == null) {
            return;
        }
        GHEventPayload.IssueComment issueComment = gitHub.parseEventPayload(new StringReader(payload), GHEventPayload.IssueComment.class);
        LOGGER.finest(MessageFormat.format("Comment on {0} from {1}: {2}", 
                issueComment.getIssue().getUrl(), issueComment.getComment().getUser(), issueComment.getComment().getBody()));
        if (!"created".equals(issueComment.getAction())) {
            LOGGER.finest(MessageFormat.format("Unsupported issue_comment action: ''{0}''", issueComment.getAction()));
            return;
        }
        
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            for (AbstractProject<?,?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                PullRequestBuildTrigger trigger = job.getTrigger(PullRequestBuildTrigger.class);
                if (trigger != null && trigger.getBuildHandler() instanceof PullRequestBuildHandler) {
                    ((PullRequestBuildHandler) trigger.getBuildHandler()).handle(issueComment, gitHub);
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }                            

    }

    @Extension
    public static final class BuilderListenerImpl extends BuilderListener {

        @Override
        public void onDeploying(AbstractBuild<?, ?> build, String instanceId, ElasticBoxCloud cloud) throws IOException, InterruptedException {
            AbstractBuild<?, ?> rootBuild = build;
            for (Cause.UpstreamCause upstreamCause = build.getCause(Cause.UpstreamCause.class); upstreamCause != null;
                    upstreamCause = rootBuild.getCause(Cause.UpstreamCause.class)) {
                Run<?, ?> run = upstreamCause.getUpstreamRun();
                if (run == null) {
                    break;
                }
                rootBuild = (AbstractBuild<?, ?>) run;
            }
            TriggerCause cause = rootBuild.getCause(TriggerCause.class);
            if (cause == null) {
                return;
            }
            ConcurrentHashMap<String, PullRequestData> prDataLookup = getInstance().projectPullRequestDataLookup.get(rootBuild.getProject());
            if (prDataLookup != null) {
                PullRequestData data = prDataLookup.get(cause.getPullRequest().getHtmlUrl().toString());
                if (data == null) {
                    data = getInstance().addPullRequestData(cause.getPullRequest(), rootBuild.getProject());
                }
                data.getInstances().add(new PullRequestInstance(instanceId, cloud.name));
                data.save();
            }
        }

        @Override
        public void onTerminating(AbstractBuild<?, ?> build, String instanceId, ElasticBoxCloud cloud) throws IOException, InterruptedException {
            for (ConcurrentHashMap<String, PullRequestData> prDataLookup : getInstance().projectPullRequestDataLookup.values()) {
                for (PullRequestData data : prDataLookup.values()) {
                    for (Iterator<PullRequestInstance> it = data.getInstances().iterator(); it.hasNext();) {
                        if (it.next().id.equals(instanceId)) {
                            it.remove();
                            data.save();
                            return;
                        }                        
                    }
                }
            }
        }
        
    }      
    
    @Extension
    public static class ProjectDataListenerImpl extends ProjectDataListener {

        @Override
        protected void onLoad(ProjectData projectData) {
            LOGGER.finest(MessageFormat.format("Loaded ElasticBox specific data of project ''{0}''", 
                    projectData.getProject().getName()));
            PullRequestManager manager = PullRequestManager.getInstance();
            PullRequests pullRequests = projectData.get(PullRequests.class);
            if (pullRequests != null) {
                ConcurrentHashMap<String, PullRequestData> pullRequestDataLookup = new ConcurrentHashMap<String, PullRequestData>();
                for (PullRequestData pullRequestData : pullRequests.getData()) {
                    pullRequestDataLookup.put(pullRequestData.pullRequestUrl.toString(), pullRequestData);
                }
                manager.projectPullRequestDataLookup.put(projectData.getProject(), pullRequestDataLookup);
            }                    
        }

    }
    
    @Extension
    public static class ProjectListener extends ItemListener {

        @Override
        public void onDeleted(Item item) {
            if (item instanceof AbstractProject) {
                ProjectData.removeInstance((AbstractProject) item);
            }
        }
               
    }
    
}
