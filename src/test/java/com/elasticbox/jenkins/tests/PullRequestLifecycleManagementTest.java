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
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.github.GHHook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestLifecycleManagementTest extends PullRequestTestBase {
    private static final Logger LOGGER = Logger.getLogger(PullRequestLifecycleManagementTest.class.getName() );


    @Test
    public void testPullRequestLifecycleManagement() throws Exception {

        long NEXTBUILD_TIMEOUT = 120; // before was 30
        setLoggerLevel("com.elasticbox.jenkins", Level.FINER);

        LOGGER.fine("Check GitHub webhook");
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

        LOGGER.fine("Check that the job is triggered");
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

        LOGGER.fine("Testing the sync payload...");
        testSyncPayload("b33073260dbbf2457f24965c057abcf186add98d");

        LOGGER.fine("Testing the sync payload - second run");
        testSyncPayload("553d231289338741f581dd99049f36ef5e1c5533");

        int count = 0;
        LOGGER.fine("Testing trigger Phrase...");
        final String triggerPhrase = "Jenkins test this please ";
        pullRequest.comment(triggerPhrase + (count++) );
        Assert.assertNull(MessageFormat.format("Unexpected build triggered with comment ''{0}''", triggerPhrase), waitForNextBuild(NEXTBUILD_TIMEOUT, "Waiting build triggered with comment " + triggerPhrase));

        updateTriggerPhrase(triggerPhrase);
        pullRequest.comment(triggerPhrase + (count++) );
        ensureBuildTriggered("Build is not triggered on posting trigger phrase to pull request {0} after 1 minute", pullRequest.getGHPullRequest().getHtmlUrl());

        instances.addAll(checkBuild(TestUtils.GITHUB_USER));

        pullRequest.close();
        waitForDeletion(instances, TimeUnit.MINUTES.toSeconds(10), "pullRequest.close PR#" + count);
        Assert.assertTrue("Deployed instances are not deleted after 10 minutes since the pull request is closed", instances.isEmpty());

        LOGGER.fine("Checking that the job is not triggered because the pull request is closed");
        pullRequest.comment(triggerPhrase + (count++), "closed");
        Assert.assertNull("Build is triggered even for closed pull request", waitForNextBuild(NEXTBUILD_TIMEOUT, "Waiting build triggered for closed pull request" ));

        LOGGER.fine("Enable whitelist and check that that whitelist is enforced");
        updateWhitelist(testTag);
        pullRequest.open();
        Assert.assertNull("Build is triggered even by user not in the whitelist", waitForNextBuild(NEXTBUILD_TIMEOUT, "Waiting build triggered by user not in the whitelist"));

        pullRequest.comment(triggerPhrase + (count++) );
        Assert.assertNull("Build is triggered even by comment of user not in the whitelist", waitForNextBuild(NEXTBUILD_TIMEOUT, "Waiting build triggered by comment of user not in the whitelist " + triggerPhrase + count));

        pullRequest.close();
        pullRequest.reopen();
        Assert.assertNull("Build is triggered even by user not in the whitelist", waitForNextBuild(NEXTBUILD_TIMEOUT, "Waiting build triggered by reopened PR of user not in the whitelist"));

        updateWhitelist(testTag + ',' + TestUtils.GITHUB_USER);
        pullRequest.reopen();
        ensureBuildTriggered("Build is not triggered on reopen of pull request {0} after 1 minute", pullRequest.getGHPullRequest().getHtmlUrl());

        instances.addAll(checkBuild(null));

        pullRequest.comment(triggerPhrase + (count++) );
        ensureBuildTriggered("Build is not triggered on posting trigger comment to pull request {0} after 1 minute", pullRequest.getGHPullRequest().getHtmlUrl());

        instances.addAll(checkBuild(TestUtils.GITHUB_USER));

        pullRequest.close();
        waitForDeletion(instances, TimeUnit.MINUTES.toSeconds(10));
        Assert.assertTrue("Deployed instances are not deleted after 10 minutes since the pull request is closed", instances.isEmpty());

        LOGGER.fine("Checking that the running job is aborted");
        abortBuildOfClosePullRequest(true);

        LOGGER.fine("Checking that the queued job is aborted");
        jenkinsRule.getInstance().setNumExecutors(1);
        FreeStyleProject project = (FreeStyleProject) jenkinsRule.getInstance().createProjectFromXML("test-sleep-job",
                new ByteArrayInputStream(createTestDataFromTemplate("jobs/test-sleep-job.xml").getBytes() ));
        TestUtils.runJob(project, new HashMap<String, String>(), jenkinsRule.getInstance());
        abortBuildOfClosePullRequest(false);
    }

    private void testSyncPayload(String sha) throws IOException {
        pullRequest.sync(sha);
        ensureBuildTriggered("Build is not triggered after 1 minute on synchronizing of pull request: {0}", pullRequest.getGHPullRequest().getHtmlUrl());
    }

    private void ensureBuildTriggered(String messageFormat, Object parameter) {
        final AbstractBuild build = waitForNextBuild(60, "ensureBuildTriggered");
        Assert.assertNotNull(MessageFormat.format(messageFormat, parameter), build);

        waitForCompletion(TimeUnit.MINUTES.toSeconds(15) );
        Assert.assertFalse(MessageFormat.format("Build of pull request {0} is still not complete after 15 minutes", parameter), build.isBuilding() );
    }

    private void abortBuildOfClosePullRequest(boolean waitUntilStarted) throws Exception {

        pullRequest.open();

        AbstractBuild build;
        List<Queue.Item> queue = jenkinsRule.getInstance().getQueue().getUnblockedItems();
        if (waitUntilStarted) {
            build = waitForNextBuild(60, "abortBuildOfClosePullRequest-1");
            Assert.assertNotNull(MessageFormat.format("Build is not triggered on opening of pull request {0} after 1 minutes", pullRequest.getGHPullRequest().getHtmlUrl()), build);
            Assert.assertTrue(build.isBuilding());
        } else {
            LOGGER.fine("Items in queue: " + queue);
            Assert.assertTrue(queue.size() > 0);
            Queue.Item item = queue.get(0);
            Assert.assertTrue(item.task instanceof FreeStyleProject); ;
            build = ((FreeStyleProject) item.task).getLastBuild();
            Assert.assertEquals("Item in queue not expected", item.task.getName(), project.getName() );
        }

        pullRequest.close();

        if (waitUntilStarted) {
            final AbstractBuild finalBuild = build;
            new Condition() {

                @Override
                public boolean satisfied() {
                    return !finalBuild.isBuilding();
                }
            }.waitUntilSatisfied(60);
        } else {
            Thread.sleep(2000); // Wait a couple of seconds to let the close event to be processed.
        }

        queue = jenkinsRule.getInstance().getQueue().getUnblockedItems();
        Assert.assertTrue("Expected queue to be 0. Current items: " + queue, queue.size() == 0);

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
