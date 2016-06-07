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

package com.elasticbox.jenkins.tests;

import com.elasticbox.jenkins.util.Condition;
import hudson.model.AbstractBuild;
import hudson.model.Result;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.github.GHHook;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestLifecycleManagementTest extends PullRequestTestBase {

    @Test
    public void testPullRequestLifecycleManagement() throws Exception {
        // check GitHub webhook
        Thread.sleep(5000);
        List<GHHook> hooks = gitHubRepo.getHooks();
        GHHook webhook = null;
        for (GHHook hook : hooks) {
            if ("web".equals(hook.getName()) && webhookUrl.equals(hook.getConfig().get("url"))) {
                webhook = hook;
                break;
            }
        }
        Assert.assertNotNull(MessageFormat.format("Webhook {0} is not created for repository {1}", webhookUrl, gitHubRepo.getHtmlUrl()), webhook);

        pullRequest.open();

        // check that the job is triggered
        new Condition() {

            public boolean satisfied() {
                return project.getLastBuild() != null;
            }

        }.waitUntilSatisfied(60);

        Assert.assertNotNull(MessageFormat.format("Build is not triggered on opening of pull request {0} after 1 minutes", pullRequest.getGHPullRequest().getHtmlUrl()), project.getLastBuild());

        waitForCompletion(TimeUnit.MINUTES.toSeconds(15));
        Assert.assertFalse(MessageFormat.format("Build of pull request {0} is still not complete after 15 minutes", pullRequest.getGHPullRequest().getHtmlUrl()), project.getLastBuild().isBuilding());

        final List<JSONObject> instances = new ArrayList<JSONObject>();
        instances.addAll(checkBuild(null));

        // Testing the sync payload:
        testSyncPayload("0fd46d038e2f1d80ab5c488284c1244e234266da");

        // Testing the sync payload - second run:
        testSyncPayload("48c5e45a77ee6247d7389d3a8d7d74d0869b88c7");

        final String triggerPhrase = "Jenkins test this please";
        pullRequest.comment(triggerPhrase);
        Assert.assertNull(MessageFormat.format("Unexpected build triggered with comment ''{0}''", triggerPhrase),
                waitForNextBuild(30));

        updateTriggerPhrase(triggerPhrase);
        pullRequest.comment(triggerPhrase);
        ensureBuildTriggered("Build is not triggered on posting trigger phrase to pull request {0} after 1 minute", pullRequest.getGHPullRequest().getHtmlUrl());

        instances.addAll(checkBuild(TestUtils.GITHUB_USER));

        pullRequest.close();
        waitForDeletion(instances, TimeUnit.MINUTES.toSeconds(10));
        Assert.assertTrue("Deployed instances are not deleted after 10 minutes since the pull request is closed", instances.isEmpty());

        pullRequest.comment(triggerPhrase);
        // check that the job is not triggered because the pull request is closed
        Assert.assertNull("Build is triggered even for closed pull request", waitForNextBuild(30));

        // enable whitelist and check that that whitelist is enforced
        updateWhitelist(testTag);
        pullRequest.open();
        Assert.assertNull("Build is triggered even by user not in the whitelist", waitForNextBuild(30));

        pullRequest.comment(triggerPhrase);
        Assert.assertNull("Build is triggered even by comment of user not in the whitelist", waitForNextBuild(30));

        pullRequest.close();
        pullRequest.reopen();
        Assert.assertNull("Build is triggered even by user not in the whitelist", waitForNextBuild(30));

        updateWhitelist(testTag + ',' + TestUtils.GITHUB_USER);
        pullRequest.reopen();
        ensureBuildTriggered("Build is not triggered on reopen of pull request {0} after 1 minute", pullRequest.getGHPullRequest().getHtmlUrl());

        instances.addAll(checkBuild(null));

        pullRequest.comment(triggerPhrase);
        ensureBuildTriggered("Build is not triggered on posting trigger comment to pull request {0} after 1 minute", pullRequest.getGHPullRequest().getHtmlUrl());

        instances.addAll(checkBuild(TestUtils.GITHUB_USER));

        pullRequest.close();
        waitForDeletion(instances, TimeUnit.MINUTES.toSeconds(10));
        Assert.assertTrue("Deployed instances are not deleted after 10 minutes since the pull request is closed", instances.isEmpty());

        abortBuildOfClosePullRequest();
    }

    private void testSyncPayload(String sha) throws IOException {
        pullRequest.sync(sha);
        ensureBuildTriggered("Build is not triggered after 1 minute on synchronizing of pull request: ", pullRequest.getGHPullRequest().getHtmlUrl());
    }

    private void ensureBuildTriggered(String messageFormat, Object parameter) {
        final AbstractBuild build = waitForNextBuild(60);
        Assert.assertNotNull(MessageFormat.format(messageFormat, parameter), build);

        waitForCompletion(TimeUnit.MINUTES.toSeconds(15) );
        Assert.assertFalse(MessageFormat.format("Build of pull request {0} is still not complete after 15 minutes", parameter), build.isBuilding() );
    }

    private void abortBuildOfClosePullRequest() throws Exception {
        pullRequest.open();
        // check that the job is triggered
        final AbstractBuild build = waitForNextBuild(60);
        Assert.assertNotNull(MessageFormat.format("Build is not triggered on opening of pull request {0} after 1 minutes", pullRequest.getGHPullRequest().getHtmlUrl()), build);
        Assert.assertTrue(build.isBuilding());
        pullRequest.close();
        new Condition() {

            @Override
            public boolean satisfied() {
                return !build.isBuilding();
            }
        }.waitUntilSatisfied(60);

        for (Object instance : cloud.getClient().getInstances(TestUtils.TEST_WORKSPACE)) {
            JSONObject instanceJson = (JSONObject) instance;
            JSONArray instanceTags = instanceJson.getJSONArray("tags");
            if (instanceTags.contains(testTag)) {
                deleteAfter(instanceJson);
            }
        }

        Assert.assertEquals("Build is not aborted after the pull request is closed", Result.ABORTED, build.getResult());
    }

}
