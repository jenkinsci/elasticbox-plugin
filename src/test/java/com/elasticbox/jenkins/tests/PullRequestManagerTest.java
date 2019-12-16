/*
 * ElasticBox Confidential
 * Copyright (c) 2019 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.tests;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHIssueState;


public class PullRequestManagerTest extends PullRequestTestBase {
    private static final Logger LOGGER = Logger.getLogger(PullRequestManagerTest.class.getName() );

    private long NEXTBUILD_TIMEOUT = 30;
    private long NEXTBUILD_TIMEOUT_LONG = 60;
    private long COMPLETE_BUILD_TIMEOUT = 180;
    private long CLEARING_ALL_TIMEOUT = 300;
    private int MIN_NECESSARY_EXECUTORS = 3;
    private final String TEST_TRIGGER_PHRASE = "Jenkins test this please ";

    @Before
    @Override
    public void setup() throws Exception {
        setLoggerLevel("com.elasticbox.jenkins", Level.FINER);

        int numExecutors = jenkinsRule.getInstance().getNumExecutors();
        if(numExecutors < MIN_NECESSARY_EXECUTORS ){
            jenkinsRule.getInstance().setNumExecutors(MIN_NECESSARY_EXECUTORS);
        }
        super.setup();
    }

    @Test
    public void testPullRequestsForMultipleProjects() throws Exception {

        LOGGER.fine("Check GitHub webhook");
        Thread.sleep(5000); // Wait for webhook be created
        List<GHHook> hooks = gitHubRepo.getHooks();
        GHHook webhook = null;
        for (GHHook hook : hooks) {
            if ("web".equals(hook.getName()) && webhookUrl.equals(hook.getConfig().get("url"))) {
                webhook = hook;
                break;
            }
        }
        Assert.assertNotNull(MessageFormat.format("Webhook {0} is not created for repository {1}", webhookUrl, gitHubRepo.getHtmlUrl()), webhook);

        LOGGER.fine("launching one pull request that triggers job 1");
        pullRequest.open();
        waitForBuildTriggered(project, NEXTBUILD_TIMEOUT_LONG, "testPullRequestsForMultipleProjects");

        Assert.assertNotNull(MessageFormat.format("Build is not triggered on opening of pull request {0} after {1} minutes",
                pullRequest.getGHPullRequest().getHtmlUrl(), TimeUnit.SECONDS.toMinutes(NEXTBUILD_TIMEOUT_LONG)),
                project.getLastBuild() );

        LOGGER.fine("Creating new job 2 listening trigger from the same repository...");
        FreeStyleProject project2 = createJenkinsJob2();

        testPullRequestsAfterAddingProject(project2);

        testSamePullRequestsTriggersMultipleProjects(project2);

    }

    public void testPullRequestsAfterAddingProject(FreeStyleProject project2) throws Exception {
        final List<JSONObject> instances = new ArrayList<JSONObject>();

        LOGGER.fine("test ---- testPullRequestsAfterAddingProject");

        try {
            // Check that job 2 has no builds
            waitForBuildTriggered(project2, NEXTBUILD_TIMEOUT, "testAddingProjectToGHRepository 0");

            Assert.assertNull(MessageFormat.format("Build in job 2 should not be triggered yet, on opening of pull request {0} after {1} minutes",
                    pullRequest.getGHPullRequest().getHtmlUrl(), TimeUnit.SECONDS.toMinutes(NEXTBUILD_TIMEOUT)),
                    project2.getLastBuild() );

            // Wait till existing trigger Phrase is built, then send new trigger comment to both projects


            int count = 0;
            LOGGER.fine("Testing trigger Phrase...");
            final String triggerPhrase = TEST_TRIGGER_PHRASE;
            pullRequest.comment(triggerPhrase + (count++) );
            Assert.assertNull(MessageFormat.format("Unexpected build triggered with comment ''{0}''",
                    triggerPhrase),
                    waitForNextBuild(project, NEXTBUILD_TIMEOUT, "Waiting build triggered with comment " + triggerPhrase));

            updateTriggerPhrase(project, triggerPhrase);
            updateTriggerPhrase(project2, triggerPhrase);
            pullRequest.comment(triggerPhrase + (count++) );
            // next build trigger event is the same for both projects. Any from both projects can be used to address this event
            waitForNextBuild(project, NEXTBUILD_TIMEOUT, "ensureBuildTriggeredInProject");

            // Test both projects building this pullRequest and this commit

            LOGGER.fine("Check that the job 1 build is started");
            waitForBuildTriggered(project, NEXTBUILD_TIMEOUT_LONG, "testAddingProjectToGHRepository 1");

            Assert.assertNotNull(MessageFormat.format("Build 1 is not triggered on opening of pull request {0} after {1} minutes",
                    pullRequest.getGHPullRequest().getHtmlUrl(), TimeUnit.SECONDS.toMinutes(NEXTBUILD_TIMEOUT_LONG)),
                    project.getLastBuild() );


            LOGGER.fine("Check that the job 2 build is started");
            waitForBuildTriggered(project2, NEXTBUILD_TIMEOUT, "testAddingProjectToGHRepository 2");

            Assert.assertNotNull(MessageFormat.format("Build 2 is not triggered on opening of pull request {0} after {1} minutes",
                    pullRequest.getGHPullRequest().getHtmlUrl(), TimeUnit.SECONDS.toMinutes(NEXTBUILD_TIMEOUT_LONG + NEXTBUILD_TIMEOUT)),
                    project2.getLastBuild());

            // Tests success. Finishing...
            LOGGER.fine("Test completed succesfully. Cleaning...");

            // Ensure triggered builds are completed
            waitForCompletion(project, COMPLETE_BUILD_TIMEOUT );
            Assert.assertFalse(MessageFormat.format("Build of pull request {0} for project {1} is still not complete after 3 minutes",
                    pullRequest.getGHPullRequest().getHtmlUrl(), project.getName()),
                    project.getLastBuild().isBuilding() );

            waitForCompletion(project2, COMPLETE_BUILD_TIMEOUT );
            Assert.assertFalse(MessageFormat.format("Build of pull request {0} for project {1} is still not complete after 3 minutes",
                    pullRequest.getGHPullRequest().getHtmlUrl(), project2.getName()),
                    project2.getLastBuild().isBuilding() );

            LOGGER.fine("Closing instances from project 1");
            instances.addAll(checkBuild(project, TestUtils.GITHUB_USER));

        } catch (Exception ex) {
            throw ex;
        } finally {
            if (pullRequest.getGHPullRequest().getState() == GHIssueState.OPEN) {
                LOGGER.fine("Closing the pull request and deleting " + instances.size() + " instances.");
                pullRequest.close();
                waitForDeletion(instances, CLEARING_ALL_TIMEOUT);
                Assert.assertTrue(MessageFormat.format("Deployed instances are not deleted after {0} minutes since the pull request is closed",
                        TimeUnit.SECONDS.toMinutes(CLEARING_ALL_TIMEOUT)), instances.isEmpty());
            }
            LOGGER.fine("End of test testAddingProjectToGHRepository");
        }
    }


    public void testSamePullRequestsTriggersMultipleProjects(FreeStyleProject project2) throws Exception {
        final List<JSONObject> instances = new ArrayList<JSONObject>();

        LOGGER.fine("test ---- testSamePullRequestsTriggersMultipleProjects");
        LOGGER.fine("Launching one pull request that triggers two jobs");

        try {
            pullRequest.open();
            waitForNextBuild(project, NEXTBUILD_TIMEOUT_LONG, "testSamePullRequestsTriggersMultipleProjects 1");

            LOGGER.fine("Check that the job 1 build is triggered");
            waitForBuildTriggered(project, NEXTBUILD_TIMEOUT_LONG, "testSamePullRequestsTriggersMultipleProjects 2");

            Assert.assertTrue(MessageFormat.format("Build is not triggered on opening of pull request {0} after {1} minutes",
                    pullRequest.getGHPullRequest().getHtmlUrl(), TimeUnit.SECONDS.toMinutes(NEXTBUILD_TIMEOUT)),
                    project.getLastBuild().isBuilding());

            LOGGER.fine("Check that the job 2 build is triggered");
            waitForBuildTriggered(project2, NEXTBUILD_TIMEOUT, "testSamePullRequestsTriggersMultipleProjects 3");

            Assert.assertTrue(MessageFormat.format("Build is not triggered on opening of pull request {0} after {1} minutes",
                    pullRequest.getGHPullRequest().getHtmlUrl(), TimeUnit.SECONDS.toMinutes(NEXTBUILD_TIMEOUT)),
                    project2.getLastBuild().isBuilding());

            // Tests success. Finishing...
            LOGGER.fine("Test completed succesfully. Cleaning...");

            LOGGER.fine("Waiting for completion of buildings...");
            waitForCompletion(project, COMPLETE_BUILD_TIMEOUT, "testSamePullRequestsTriggersMultipleProjects 4");
            Assert.assertFalse(MessageFormat.format("Job 1 Build of pull request {0} is still not complete after {1} minutes", pullRequest.getGHPullRequest().getHtmlUrl(), COMPLETE_BUILD_TIMEOUT),
                    project.getLastBuild().isBuilding());

            waitForCompletion(project2, COMPLETE_BUILD_TIMEOUT, "testSamePullRequestsTriggersMultipleProjects 5");
            Assert.assertFalse(MessageFormat.format("Job 2 Build of pull request {0} is still not complete after {1} minutes", pullRequest.getGHPullRequest().getHtmlUrl(), COMPLETE_BUILD_TIMEOUT),
                    project2.getLastBuild().isBuilding());

            LOGGER.fine("Closing the pull request 1");
            instances.addAll(checkBuild(project, null));

        } catch (Exception ex) {
            throw ex;
        } finally {
            if(pullRequest.getGHPullRequest().getState() == GHIssueState.OPEN) {
                LOGGER.fine("Closing the pull request and deleting " + instances.size() + " instances.");
                pullRequest.close();
                waitForDeletion(instances, CLEARING_ALL_TIMEOUT, "testSamePullRequestsTriggersMultipleProjects 6");
                Assert.assertTrue(MessageFormat.format("Deployed instances are not deleted after {0} minutes since the pull request is closed",
                        TimeUnit.SECONDS.toMinutes(CLEARING_ALL_TIMEOUT)), instances.isEmpty());
            }
            LOGGER.fine("End of testSamePullRequestsTriggersMultipleProjects");
        }


    }

    private FreeStyleProject createJenkinsJob2 () throws Exception {
        String testTag = UUID.randomUUID().toString().substring(0, 30);
        String projectName = "test-pull-request-2";

        TestUtils.TemplateResolver templateResolver = new TemplateResolveImpl() {

            @Override
            public String resolve(String template) {
                String repoAddress = MessageFormat.format("{0}/{1}", TestUtils.GITHUB_ADDRESS, GIT_REPO);
                return getTemplateResolver().resolve(template).replace("${TEST_TAG}", testTag)
                        .replace("${GITHUB_PROJECT_URL}", repoAddress);
            }

        };

        return (FreeStyleProject) jenkinsRule.getInstance().createProjectFromXML(projectName,
                new ByteArrayInputStream(templateResolver.resolve(TestUtils.getResourceAsString("jobs/test-pull-request.xml")).getBytes()));

    }

}
