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

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.elasticbox.jenkins.util.Condition;
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
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
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
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.github.GitHubPlugin;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.jenkinsci.plugins.github.config.GitHubTokenCredentialsCreator;
import org.junit.Assert;
import org.junit.Before;
import org.kohsuke.github.*;
import org.w3c.dom.Document;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestTestBase extends BuildStepTestBase {
    private static final Logger LOGGER = Logger.getLogger(PullRequestTestBase.class.getName());
    
    private static final String GIT_REPO = MessageFormat.format("{0}/{1}", TestUtils.GITHUB_USER, TestUtils.GITHUB_REPO_NAME);
    private static final String PR_TITLE_PREFIX = "ElasticBox Jenkins plugin test PR ";
    private static final String PR_TITLE = PR_TITLE_PREFIX + UUID.randomUUID().toString();
    private static final String PR_DESCRIPTION = "Automatic test PR from ElasticBox Jenkins plugin";

    protected String testTag;
    protected MockPullRequest pullRequest;
    protected FreeStyleProject project, downstreamProject;
    protected String webhookUrl;
    protected GHRepository gitHubRepo;
    protected String apiGithubAddress;

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        boolean customApiUrl;
        if (TestUtils.GITHUB_ADDRESS.equals(MessageFormat.format("https://{0}", TestUtils.GITHUB_PUBLIC_ADDRESS))) {
            apiGithubAddress = MessageFormat.format("https://api.{0}", TestUtils.GITHUB_PUBLIC_ADDRESS);
            customApiUrl = false;
        } else {
            apiGithubAddress = MessageFormat.format("{0}/api/v3", TestUtils.GITHUB_ADDRESS);
            customApiUrl = true;
        }
        webhookUrl = ((PullRequestBuildTrigger.DescriptorImpl) jenkins.getInstance().getDescriptor(PullRequestBuildTrigger.class)).getWebHookUrl();
        GitHub gitHub = createGitHubConnection(TestUtils.GITHUB_ADDRESS, TestUtils.GITHUB_USER, TestUtils.GITHUB_ACCESS_TOKEN);
        gitHubRepo = gitHub.getRepository(GIT_REPO);
        // try to delete all hooks
        try {
            for (GHHook hook : gitHubRepo.getHooks()) {
                hook.delete();
            }
        } catch (FileNotFoundException ex) {
            LOGGER.warning("No hooks defined for this " + gitHubRepo);
        } catch (Exception ex) {
            LOGGER.warning("Error while trying to delete hooks from Repo: " + gitHubRepo);
        }

        GHPullRequest ghPullRequest = getTestPullRequest(gitHubRepo);
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
        
        StandardCredentials creds = jenkins.getInstance()
                .getDescriptorByType(GitHubTokenCredentialsCreator.class)
                .createCredentials(apiGithubAddress, TestUtils.GITHUB_ACCESS_TOKEN, TestUtils.GITHUB_USER);
        GitHubServerConfig config = new GitHubServerConfig(creds.getId());
        config.setCustomApiUrl(customApiUrl);
        config.setApiUrl(apiGithubAddress);
        config.setManageHooks(true);
        GitHubPlugin.configuration().getConfigs().add(config);
        
        TestUtils.TemplateResolver templateResolver = new TemplateResolveImpl() {

            @Override
            public String resolve(String template) {
                String repoAddress = MessageFormat.format("{0}/{1}", TestUtils.GITHUB_ADDRESS, GIT_REPO);
                 return getTemplateResolver().resolve(template).replace("${TEST_TAG}", testTag)
                         .replace("${GITHUB_PROJECT_URL}", repoAddress);
            }

        };
        downstreamProject = (FreeStyleProject) jenkins.getInstance().createProjectFromXML("test-pull-request-downstream",
                new ByteArrayInputStream(templateResolver.resolve(TestUtils.getResourceAsString("jobs/test-pull-request-downstream.xml")).getBytes()));
        project = (FreeStyleProject) jenkins.getInstance().createProjectFromXML("test-pull-request",
                new ByteArrayInputStream(templateResolver.resolve(TestUtils.getResourceAsString("jobs/test-pull-request.xml")).getBytes()));
    }

    private GHPullRequest getTestPullRequest(GHRepository githubRepo) throws IOException {
        GHPullRequest ghPullRequest = null;
        List<GHPullRequest> openedPullRequests = githubRepo.getPullRequests(GHIssueState.OPEN);
        for (GHPullRequest pullRequest : openedPullRequests) {
            if(pullRequest.getTitle().startsWith(PR_TITLE_PREFIX)){
                LOGGER.info("Found an existing open PR, reusing it: " + pullRequest.getTitle() );
                ghPullRequest = pullRequest;
                break;
            }
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        if (ghPullRequest != null && ghPullRequest.getCreatedAt().before(calendar.getTime())) {
            // We close the PR to avoid to reach the max limit of PR actions
            ghPullRequest.close();
            ghPullRequest = null;
        }

        if (ghPullRequest == null) {
            ghPullRequest = githubRepo.createPullRequest(PR_TITLE, TestUtils.GITHUB_TEST_BRANCH, githubRepo.getDefaultBranch(), PR_DESCRIPTION);
        }

        return ghPullRequest;
    }

    private GitHub createGitHubConnection(String githubEndpoint, String githubUser, String githubToken) throws IOException {
        GitHub github;
        String publicGithubAddress = MessageFormat.format("https://{0}", TestUtils.GITHUB_PUBLIC_ADDRESS);
        if (githubEndpoint.equals(publicGithubAddress)) {
            github = GitHub.connect(githubUser, githubToken);
        } else {
            github = GitHub.connectToEnterprise(apiGithubAddress, githubUser, githubToken);
        }

        return github;
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
        Assert.assertEquals(parameters.get(PullRequestBuildHandler.PR_URL), pullRequest.getGHPullRequest().getHtmlUrl().toString());

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
        private final String syncPullRequestPayload;
        private final String commentPullRequestPayloadTemplate;
        private final GHPullRequest ghPullRequest;

        private StringEntity createPayload(String content) throws UnsupportedEncodingException {
            return new StringEntity("payload=" + URLEncoder.encode(content, "UTF-8"), ContentType.APPLICATION_FORM_URLENCODED);
        }

        public MockPullRequest(GHPullRequest ghPullRequest) throws Exception {
            this.ghPullRequest = ghPullRequest;
            HashMap<String, Object> jinjaContext = createJinjaContext();
            openPullRequestPayload = createPayload(TestUtils.JINJA_RENDER.render(TestUtils.getResourceAsString("test-pull-request-opened.json"), jinjaContext));
            closePullRequestPayload = createPayload(TestUtils.JINJA_RENDER.render(TestUtils.getResourceAsString("test-pull-request-closed.json"), jinjaContext));
            reopenPullRequestPayload = createPayload(TestUtils.JINJA_RENDER.render(TestUtils.getResourceAsString("test-pull-request-reopened.json"), jinjaContext));
            syncPullRequestPayload = TestUtils.JINJA_RENDER.render(TestUtils.getResourceAsString("test-pull-request-synchronize.json"), jinjaContext);
            commentPullRequestPayloadTemplate = TestUtils.JINJA_RENDER.render(TestUtils.getResourceAsString("test-github-issue-comment-created.json"), jinjaContext);
        }

        private HashMap<String, Object> createJinjaContext() {
            HashMap<String, Object> jinjaContext = new HashMap<String, Object>();

            String prUrl = ghPullRequest.getHtmlUrl().toString();
            List<GHPullRequestCommitDetail> commits = ghPullRequest.listCommits().asList();
            jinjaContext.put("GITHUB_ADDRESS", TestUtils.GITHUB_ADDRESS.replace("https://", ""));
            jinjaContext.put("API_GITHUB_ADDRESS", apiGithubAddress.replace("https://", ""));
            jinjaContext.put("GITHUB_USER", TestUtils.GITHUB_USER);
            jinjaContext.put("GITHUB_REPO", TestUtils.GITHUB_REPO_NAME);
            jinjaContext.put("BRANCH", TestUtils.GITHUB_TEST_BRANCH);
            jinjaContext.put("PR_NUMBER", Integer.parseInt(prUrl.substring(prUrl.lastIndexOf('/') + 1)));
            if (commits.size() > 0) {
                jinjaContext.put("COMMIT_SHA", commits.get(commits.size() - 1).getSha());
            }

            return jinjaContext;
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
            LOGGER.info("Opening PR #" + getGHPullRequest().getNumber() );
            ghPullRequest.reopen();
            postPayload(openPullRequestPayload, "pull_request");
        }

        public void reopen() throws IOException {
            LOGGER.info("Reopening PR #" + getGHPullRequest().getNumber() );
            ghPullRequest.reopen();
            postPayload(reopenPullRequestPayload, "pull_request");
        }

        public void close() throws IOException {
            LOGGER.info("Closing PR #" + getGHPullRequest().getNumber() );
            ghPullRequest.close();
            postPayload(closePullRequestPayload, "pull_request");
        }

        public void sync(String sha) throws IOException {
            LOGGER.info("Synchronizing PR #" + getGHPullRequest().getNumber() );

            String payload = StringUtils.replaceOnce(syncPullRequestPayload, "${RANDOM_SHA}", sha);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss\'Z\'");
            payload = StringUtils.replaceOnce(payload, "${TIMESTAMP}", dateFormat.format(new Date() ));

            postPayload(createPayload(payload), "pull_request");
        }

        public void comment(String comment) throws IOException {
            LOGGER.info("Commenting PR #" + getGHPullRequest().getNumber() + ": " + comment);
            postPayload(createPayload(commentPullRequestPayloadTemplate.replace("${COMMENT}", comment)), "issue_comment");
        }
    }

    private Document getProjectDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(project.getConfigFile().getFile());
    }

    protected void updateWhitelist(String whitelist) throws Exception {
        LOGGER.info("Updating Whitelist: " + whitelist);
        Document document = getProjectDocument();
        document.getElementsByTagName("whitelist").item(0).setTextContent(whitelist);
        updateProject(document);
    }

    protected void updateTriggerPhrase(String triggerPhrase) throws Exception {
        Document document = getProjectDocument();
        document.getElementsByTagName("triggerPhrase").item(0).setTextContent(triggerPhrase);
        updateProject(document);
    }

    private void updateProject(Document document) throws Exception {
        DOMSource src = new DOMSource(document);
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        StreamResult result = new StreamResult();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result.setOutputStream(out);
        transformer.transform(src, result);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Source streamSource = new StreamSource(in);
        project.updateByXml(streamSource);
    }

}
