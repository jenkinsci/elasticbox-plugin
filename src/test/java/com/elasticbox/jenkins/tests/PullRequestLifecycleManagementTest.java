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
import com.cloudbees.jenkins.Credential;
import com.cloudbees.jenkins.GitHubPushTrigger;
import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.jenkins.triggers.PullRequestBuildTrigger;
import com.elasticbox.jenkins.triggers.github.PullRequestBuildHandler;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mortbay.util.ajax.WaitingContinuation;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestLifecycleManagementTest extends BuildStepTestBase {
    private static final Logger LOGGER = Logger.getLogger(PullRequestLifecycleManagementTest.class.getName());
    private static final String GIT_REPO = TestUtils.GITHUB_USER + "/elasticbox-plugin";
    private String testTag;
    private MockPullRequest pullRequest;
    private FreeStyleProject project, downstreamProject;
    private String webhookUrl;
    private GHRepository gitHubRepo;
    
    @Before
    @Override
    public void setup() throws Exception {
        super.setup();  
        webhookUrl = ((PullRequestBuildTrigger.DescriptorImpl) jenkins.getInstance().getDescriptor(PullRequestBuildTrigger.class)).getWebHookUrl();        
        GitHub gitHub = GitHub.connect(TestUtils.GITHUB_USER, TestUtils.GITHUB_ACCESS_TOKEN);
        gitHubRepo = gitHub.getRepository(GIT_REPO);
        for (GHHook hook : gitHubRepo.getHooks()) {
            hook.delete();
        }
        GHPullRequest ghPullRequest = gitHubRepo.getPullRequest(1);
        ghPullRequest.close();
        pullRequest = new MockPullRequest(ghPullRequest);
        testTag = UUID.randomUUID().toString().substring(0, 30);
        GitHubPushTrigger.DescriptorImpl descriptor = (GitHubPushTrigger.DescriptorImpl) jenkins.getInstance().getDescriptor(GitHubPushTrigger.class);
        descriptor.getCredentials().add(new Credential(TestUtils.GITHUB_USER, null, TestUtils.GITHUB_ACCESS_TOKEN));
        TestUtils.TemplateResolver templateResolver = new TemplateResolveImpl() {

            @Override
            public String resolve(String template) {
                 return getTemplateResolver().resolve(template).replace("${TEST_TAG}", testTag)
                         .replace("${GITHUB_PROJECT_URL}", "https://github.com/" + GIT_REPO);
            }
            
        };
        downstreamProject = (FreeStyleProject) jenkins.getInstance().createProjectFromXML("test-pull-request-downstream",
                new ByteArrayInputStream(templateResolver.resolve(TestUtils.getResourceAsString("jobs/test-pull-request-downstream.xml")).getBytes()));        
        project = (FreeStyleProject) jenkins.getInstance().createProjectFromXML("test-pull-request", 
                new ByteArrayInputStream(templateResolver.resolve(TestUtils.getResourceAsString("jobs/test-pull-request.xml")).getBytes()));
    }

    private Map<String, String> getStringParameters(AbstractBuild build) {
        Map<String, String> parameters = new HashMap<String, String>();
        ParametersAction paramsAction = build.getAction(ParametersAction.class);     
        if (paramsAction != null) {
            for (ParameterValue param : paramsAction.getParameters()) {
                if (param instanceof StringParameterValue) {
                    parameters.put(param.getName(), ((StringParameterValue) param).value);
                }
            }
        }
        return parameters;
    }
    
    private List<JSONObject> checkBuild(String buildRequester) throws Exception {
        AbstractBuild<?, ?> build = project.getLastBuild();
        try {
            TestUtils.assertBuildSuccess(build);
        } catch (AssertionError error) {
            if (downstreamProject.getLastFailedBuild() != null) {
                LOGGER.severe(TestUtils.getLog(downstreamProject.getLastBuild()));
            }
            throw error;
        }
        
        Map<String, String> parameters = getStringParameters(build);
        if (buildRequester == null) {
            Assert.assertNull(parameters.get(PullRequestBuildHandler.BUILD_REQUESTER));
        } else {
            Assert.assertEquals(parameters.get(PullRequestBuildHandler.BUILD_REQUESTER), buildRequester);
        }
        Assert.assertEquals(parameters.get(PullRequestBuildHandler.PR_COMMIT), pullRequest.getGHPullRequest().getHead().getSha());
        Assert.assertEquals(parameters.get(PullRequestBuildHandler.PR_BRANCH), pullRequest.getGHPullRequest().getHead().getRef());
        Assert.assertEquals(parameters.get(PullRequestBuildHandler.PR_NUMBER), String.valueOf(pullRequest.getGHPullRequest().getNumber()));
        Assert.assertEquals(parameters.get(PullRequestBuildHandler.PR_MERGE_BRANCH), pullRequest.getGHPullRequest().getBase().getRef());
        Assert.assertEquals(parameters.get(PullRequestBuildHandler.PR_OWNER), pullRequest.getGHPullRequest().getUser().getLogin());
        Assert.assertEquals(parameters.get(PullRequestBuildHandler.PR_URL), pullRequest.getGHPullRequest().getUrl().toString());

        // check that instances are deployed
        Client client = cloud.getClient();
        Collection<String> mainProjectInstanceTags = Arrays.asList(testTag, MessageFormat.format("{0}_{1}", 
                project.getName(), pullRequest.getGHPullRequest().getNumber()));        
        Collection<String> downstreamProjectInstanceTags = Arrays.asList(testTag, MessageFormat.format("{0}_{1}", 
                downstreamProject.getName(), pullRequest.getGHPullRequest().getNumber()));
        JSONObject mainProjectInstance = null, downstreamProjectInstance = null;
        for (Object instance : client.getInstances(TestUtils.TEST_WORKSPACE)) {
            JSONObject instanceJson = (JSONObject) instance;
            JSONArray instanceTags = instanceJson.getJSONArray("tags");
            if (instanceTags.containsAll(mainProjectInstanceTags)) {
                mainProjectInstance = instanceJson;
            } else if (instanceTags.containsAll(downstreamProjectInstanceTags)) {
                downstreamProjectInstance = instanceJson;
            }
            if (mainProjectInstance != null && downstreamProjectInstance != null) {
                break;
            }
        }
        Assert.assertNotNull(MessageFormat.format("Job {0} did not deploy any instance", project.getName()), mainProjectInstance);
        deleteAfter(mainProjectInstance);
        Assert.assertNotNull(MessageFormat.format("Job {0} did not deploy any instance", downstreamProject.getName()), downstreamProjectInstance);
        deleteAfter(downstreamProjectInstance);
        
        return Arrays.asList(mainProjectInstance, downstreamProjectInstance);
    }
    
    private AbstractBuild waitForNextBuild(long timeoutSeconds) {
        final AbstractBuild build = project.getLastBuild();
        new Condition() {

            public boolean satisfied() {
                return build.getNextBuild() != null;
            }
            
        }.waitUntilSatisfied(timeoutSeconds);
        return build.getNextBuild();
    }
    
    private void waitForCompletion(long timeoutSeconds) {
        final AbstractBuild build = project.getLastBuild();
        new Condition() {

            public boolean satisfied() {
                return !build.isBuilding();
            }
            
        }.waitUntilSatisfied(timeoutSeconds);
    }
    
    private void waitForDeletion(final List<JSONObject> instances, long timeoutSeconds) {
        new Condition() {

            public boolean satisfied() {
                try {
                    Client client = cloud.getClient();
                    for (Iterator<JSONObject> iter = instances.iterator(); iter.hasNext();) {
                        JSONObject instance = iter.next();
                        try {
                            client.getInstance(instance.getString("id"));
                        } catch (ClientException ex) {
                            if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                                iter.remove();
                                objectsToDelete.remove(instance);
                            } else {
                                throw ex;
                            }
                        }
                    }
                    return instances.isEmpty();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    return false;
                }
            }
            
        }.waitUntilSatisfied(TimeUnit.MINUTES.toSeconds(timeoutSeconds));        
    }
    
    private class MockPullRequest {
        private final StringEntity openPullRequestPayload;
        private final StringEntity closePullRequestPayload;
        private final StringEntity reopenPullRequestPayload;
        private final String commentPullRequestPayloadTemplate;
        private final GHPullRequest ghPullRequest;
        
        private StringEntity createPayload(String content) throws UnsupportedEncodingException {
            return new StringEntity("payload=" + URLEncoder.encode(content, "UTF-8"), ContentType.APPLICATION_FORM_URLENCODED);
        }

        public MockPullRequest(GHPullRequest ghPullRequest) throws Exception {
            this.ghPullRequest = ghPullRequest;
            openPullRequestPayload = createPayload(TestUtils.getResourceAsString("test-pull-request-opened.json"));
            closePullRequestPayload = createPayload(TestUtils.getResourceAsString("test-pull-request-closed.json"));
            reopenPullRequestPayload = createPayload(TestUtils.getResourceAsString("test-pull-request-reopened.json"));
            commentPullRequestPayloadTemplate = TestUtils.getResourceAsString("test-github-issue-comment-created.json");            
        }

        public GHPullRequest getGHPullRequest() {
            return ghPullRequest;
        }
        
        private void postPayload(HttpEntity entity, String event) throws IOException {
            HttpPost post = new HttpPost(webhookUrl + "?.crumb=test");
            post.addHeader("X-GitHub-Event", event);
            post.setEntity(entity);
            HttpResponse response = Client.getHttpClient().execute(post);
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status > 299) {
                throw new ClientException(Client.getResponseBodyAsString(response), status);
            } else {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
        
        public void open() throws IOException {
            ghPullRequest.reopen();
            postPayload(openPullRequestPayload, "pull_request");
        }
        
        public void reopen() throws IOException {
            ghPullRequest.reopen();
            postPayload(reopenPullRequestPayload, "pull_request");
        }

        public void close() throws IOException {
            ghPullRequest.close();
            postPayload(closePullRequestPayload, "pull_request");
        }

        public void comment(String comment) throws IOException {
            postPayload(createPayload(commentPullRequestPayloadTemplate.replace("${COMMENT}", comment)), "issue_comment");
        }
    }
    
    private void updateProject(String configXml) throws IOException {
        project.updateByXml(new SAXSource(new InputSource(new ByteArrayInputStream(configXml.getBytes()))));     
    }
    
    private Document getProjectDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(project.getConfigFile().getFile());        
    }
    
    private void updateWhitelist(String whitelist) throws Exception {
        Document document = getProjectDocument();
        document.getElementsByTagName("whitelist").item(0).setTextContent(whitelist);
        project.updateByXml(new DOMSource(document));
    }
    
    private void updateTriggerPhrase(String triggerPhrase) throws Exception {
        Document document = getProjectDocument();
        document.getElementsByTagName("triggerPhrase").item(0).setTextContent(triggerPhrase);
        project.updateByXml(new DOMSource(document));
    }
    
    @Test
    public void testPullRequestLifecycleManagement() throws Exception {
        // check GitHub webhook
        Thread.sleep(3000);
        List<GHHook> hooks = gitHubRepo.getHooks();
        GHHook webhook = null;
        for (GHHook hook : hooks) {
            if ("web".equals(hook.getName()) && webhookUrl.equals(hook.getConfig().get("url"))) {
                webhook = hook;
                break;
            }
        }
        Assert.assertNotNull(MessageFormat.format("Webhook {0} is not created for repository {1}", webhookUrl, gitHubRepo.getUrl()), webhook);
        
        pullRequest.open();
        
        // check that the job is triggered
        new Condition() {

            public boolean satisfied() {
                return project.getLastBuild() != null;
            }
            
        }.waitUntilSatisfied(60);
        Assert.assertNotNull(MessageFormat.format("Build is not triggered on opening of pull request {0} after 1 minutes", pullRequest.getGHPullRequest().getUrl()), project.getLastBuild());        
        
        waitForCompletion(TimeUnit.MINUTES.toSeconds(15));
        Assert.assertFalse(MessageFormat.format("Build of pull request {0} is still not complete after 15 minutes", pullRequest.getGHPullRequest().getUrl()), project.getLastBuild().isBuilding());
        
        final List<JSONObject> instances = new ArrayList<JSONObject>();
        instances.addAll(checkBuild(null));
        
        final String triggerPhrase = "Jenkins test this please";
        pullRequest.comment(triggerPhrase);        
        Assert.assertNull(MessageFormat.format("Unexpected build triggered with comment ''{0}''", triggerPhrase), 
                waitForNextBuild(30));
        
        updateTriggerPhrase(triggerPhrase);
        pullRequest.comment(triggerPhrase);
        Assert.assertNotNull(MessageFormat.format("Build is not triggered on posting trigger phrase to pull request {0} after 1 minute", pullRequest.getGHPullRequest().getUrl()), 
                waitForNextBuild(60));
        waitForCompletion(TimeUnit.MINUTES.toSeconds(15));
        Assert.assertFalse(MessageFormat.format("Build of pull request {0} is still not complete after 15 minutes", pullRequest.getGHPullRequest().getUrl()), project.getLastBuild().isBuilding());
        
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
        AbstractBuild build = waitForNextBuild(60);
        Assert.assertNotNull("Build is not triggered after 1 minutes", build);
        waitForCompletion(TimeUnit.MINUTES.toSeconds(15));
        Assert.assertFalse(MessageFormat.format("Build of pull request {0} is still not complete after 15 minutes", pullRequest.getGHPullRequest().getUrl()), build.isBuilding());        
        instances.addAll(checkBuild(null));
        
        pullRequest.comment(triggerPhrase);
        build = waitForNextBuild(60);
        Assert.assertNotNull("Build is not triggered after 1 minutes", build);
        waitForCompletion(TimeUnit.MINUTES.toSeconds(15));
        instances.addAll(checkBuild(TestUtils.GITHUB_USER));
        
        pullRequest.close();
        waitForDeletion(instances, TimeUnit.MINUTES.toSeconds(10));
        Assert.assertTrue("Deployed instances are not deleted after 10 minutes since the pull request is closed", instances.isEmpty());        
    }
}
