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
import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxExecutor;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class PullRequestCleanup extends AsyncPeriodicWork {
    private static final long RECURRENT_PERIOD = Long.getLong(PullRequestCleanup.class.getName() + ".recurrentPeriod", TimeUnit.MINUTES.toMillis(15));
    private static final Logger LOGGER = Logger.getLogger(PullRequestCleanup.class.getName());
    
    public PullRequestCleanup() {
        super(PullRequestCleanup.class.getName());
    }

    @Override
    protected void execute(TaskListener listener) throws IOException {
        Map<String, List<PullRequestData>> pullRequestDataLookup = new HashMap<String, List<PullRequestData>>();
        Map<String, Set<String>> pullRequestURLsLookup = new HashMap<String, Set<String>>();
        for (Map<String, PullRequestData> pullRequestDataMap : PullRequestManager.getInstance().projectPullRequestDataLookup.values()) {
            for (PullRequestData pullRequestData : pullRequestDataMap.values()) {
                String pullRequestUrl = pullRequestData.pullRequestUrl.toString();
                String repoUrl = pullRequestUrl.substring(0, pullRequestUrl.lastIndexOf("/pull/"));
                Set<String> pullRequestURLs = pullRequestURLsLookup.get(repoUrl);
                if (pullRequestURLs == null) {
                    pullRequestURLs = new HashSet<String>();
                    pullRequestURLsLookup.put(repoUrl, pullRequestURLs);
                }
                pullRequestURLs.add(pullRequestUrl);                
                List<PullRequestData> pullRequestDataList = pullRequestDataLookup.get(pullRequestUrl);
                if (pullRequestDataList == null) {
                    pullRequestDataList = new ArrayList<PullRequestData>();
                    pullRequestDataLookup.put(pullRequestUrl, pullRequestDataList);
                }
                pullRequestDataList.add(pullRequestData);                
            }
        }
        for (Map.Entry<String, Set<String>> entry : pullRequestURLsLookup.entrySet()) {
            GitHubRepositoryName repoName = GitHubRepositoryName.create(entry.getKey());
            GitHub gitHub = PullRequestManager.getInstance().createGitHub(repoName);
            if (gitHub != null) {
                try {
                    GHRepository repo = gitHub.getRepository(MessageFormat.format("{0}/{1}", repoName.userName, repoName.repositoryName));
                    Set<String> openPullRequestURLs = new HashSet<String>();
                    for (GHPullRequest ghPullRequest : repo.getPullRequests(GHIssueState.OPEN)) {
                        openPullRequestURLs.add(ghPullRequest.getUrl().toString());
                    }
                    for (String pullRequestUrl : entry.getValue()) {
                        if (!openPullRequestURLs.contains(pullRequestUrl)) {
                            LOGGER.info(MessageFormat.format("Pull request {0} is closed. Deleting its data and deployed instances", pullRequestUrl));
                            List<PullRequestData> closedPullRequestDataList = pullRequestDataLookup.get(pullRequestUrl);
                            for (PullRequestData pullRequestData : closedPullRequestDataList) {
                                try {
                                    PullRequestManager.getInstance().removePullRequestData(pullRequestUrl, pullRequestData.getProject());
                                } catch (IOException ex) {
                                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                                }
                            }
                            try {
                                GHPullRequest closedPullRequest = repo.getPullRequest(getPullRequestNumber(pullRequestUrl));
                                deleteInstances(closedPullRequestDataList, closedPullRequest);
                            } catch (IOException ex) {
                                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                            }
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
                
        }
    }
    
    static int getPullRequestNumber(String url) {
        return Integer.parseInt(url.substring(url.lastIndexOf('/') + 1));
    }
    
    static void deleteInstances(List<PullRequestData> pullRequestDataList, GHPullRequest pullRequest) {
        Set<PullRequestInstance> prInstances = new HashSet<PullRequestInstance>();
        for (PullRequestData prData : pullRequestDataList) {
            prInstances.addAll(prData.getInstances());
        }
        List<String> terminatingInstanceURLs = new ArrayList<String>();
        for (PullRequestInstance instance : prInstances) {
            Client client = ClientCache.getClient(instance.cloud);
            if (client != null) {
                boolean instanceExists = true;
                try {
                    LOGGER.info(MessageFormat.format("Terminating instance {0} of pull request {1}", client.getInstanceUrl(instance.id), pullRequest.getUrl()));
                    client.terminate(instance.id);
                } catch (ClientException ex) {
                    if (ex.getStatusCode() == HttpStatus.SC_CONFLICT) {
                        try {
                            client.forceTerminate(instance.id);
                        } catch (IOException ex1) {
                            LOGGER.log(Level.SEVERE, 
                                    MessageFormat.format("Error force-terminating instance {0}", instance.id), ex1);
                        }
                    } else if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                        LOGGER.info(MessageFormat.format("Instance {0} is not found", client.getInstanceUrl(instance.id)));
                        instanceExists = false;
                    } else {
                        LOGGER.log(Level.SEVERE, MessageFormat.format("Error terminating instance {0}", instance.id), ex);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, MessageFormat.format("Error terminating instance {0}", instance.id), ex);
                }
                if (instanceExists) {
                    // add the terminating instance to the DeleteInstancesWorkload so it will be deleted after its termination
                    Jenkins.getInstance().getExtensionList(ElasticBoxExecutor.Workload.class).get(DeleteInstancesWorkload.class).add(instance);
                    terminatingInstanceURLs.add(client.getInstanceUrl(instance.id));
                }
            }
        }
        if (!terminatingInstanceURLs.isEmpty()) {
            try {
                pullRequest.comment(MessageFormat.format("The following instances are being terminated: {0}",
                        StringUtils.join(terminatingInstanceURLs, ", ")));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error posting comment to {0}", pullRequest.getUrl(), ex));
            }
        }
        
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENT_PERIOD;
    }
}
