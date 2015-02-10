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
import java.util.Arrays;
import java.util.Calendar;
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
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.w3c.dom.Document;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestTestBase extends BuildStepTestBase {
    private static final Logger LOGGER = Logger.getLogger(PullRequestTestBase.class.getName());
    private static final String GIT_REPO = TestUtils.GITHUB_USER + "/elasticbox-plugin";
    
    protected String testTag;
    protected MockPullRequest pullRequest;
    protected FreeStyleProject project, downstreamProject;
    protected String webhookUrl;
    protected GHRepository gitHubRepo;
    
    @Before
    @Override
    public void setup() throws Exception {
        super.setup();  
        webhookUrl = ((PullRequestBuildTrigger.DescriptorImpl) jenkins.getInstance().getDescriptor(PullRequestBuildTrigger.class)).getWebHookUrl();        
        GitHub gitHub = GitHub.connect(TestUtils.GITHUB_USER, TestUtils.GITHUB_ACCESS_TOKEN);
        gitHubRepo = gitHub.getRepository(GIT_REPO);
        // try to delete all hooks
        for (GHHook hook : gitHubRepo.getHooks()) {
            try {
                hook.delete();
            } catch (Exception ex) {                
            }
        }
        GHPullRequest ghPullRequest = gitHubRepo.getPullRequest(1);
        // try to delete all comments that are older than 1 hour
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, -1);        
        HttpClient httpClient = Client.getHttpClient();        
        for (GHIssueComment comment : ghPullRequest.getComments()) {
            if (comment.getCreatedAt().before(calendar.getTime())) {
                HttpDelete delete = new HttpDelete(comment.getUrl().toString());
                delete.setHeader("Authorization", MessageFormat.format("token {0}", TestUtils.GITHUB_ACCESS_TOKEN));
                HttpResponse response = null;
                try {
                    response = httpClient.execute(delete);
                } catch (Exception ex) {                    
                } finally {
                    if (response != null && response.getEntity() != null) {
                        EntityUtils.consumeQuietly(response.getEntity());
                    }
                }
            }
        }
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
    
    protected List<JSONObject> getDeployedInstances() throws IOException {
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
        
        return Arrays.asList(mainProjectInstance, downstreamProjectInstance);
    }
    
    protected List<JSONObject> checkBuild(String buildRequester) throws Exception {
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
    
    protected AbstractBuild waitForNextBuild(long timeoutSeconds) {
        final AbstractBuild build = project.getLastBuild();
        new Condition() {

            public boolean satisfied() {
                return build.getNextBuild() != null;
            }
            
        }.waitUntilSatisfied(timeoutSeconds);
        return build.getNextBuild();
    }
    
    protected void waitForCompletion(long timeoutSeconds) {
        final AbstractBuild build = project.getLastBuild();
        new Condition() {

            public boolean satisfied() {
                return !build.isBuilding();
            }
            
        }.waitUntilSatisfied(timeoutSeconds);
    }
    
    protected void waitForDeletion(final List<JSONObject> instances, long timeoutSeconds) {
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
    
    protected class MockPullRequest {
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
    
    private Document getProjectDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(project.getConfigFile().getFile());        
    }
    
    protected void updateWhitelist(String whitelist) throws Exception {
        Document document = getProjectDocument();
        document.getElementsByTagName("whitelist").item(0).setTextContent(whitelist);
        project.updateByXml(new DOMSource(document));
    }
    
    protected void updateTriggerPhrase(String triggerPhrase) throws Exception {
        Document document = getProjectDocument();
        document.getElementsByTagName("triggerPhrase").item(0).setTextContent(triggerPhrase);
        project.updateByXml(new DOMSource(document));
    }
    
}
