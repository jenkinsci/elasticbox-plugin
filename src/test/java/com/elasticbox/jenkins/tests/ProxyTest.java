package com.elasticbox.jenkins.tests;

import com.elasticbox.Client;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.ProxyConfiguration;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.logging.Logger;


public class ProxyTest {
    private final Logger LOGGER = Logger.getLogger(ProxyTest.class.getName());

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testProxyConfigured() throws Exception {

        ProxyConfiguration proxyConfiguration = new ProxyConfiguration (TestUtils.TEST_JENKINS_PROXY_NAME, TestUtils.TEST_JENKINS_PROXY_PORT,
                TestUtils.TEST_JENKINS_PROXY_USER, TestUtils.TEST_JENKINS_PROXY_PWRD);

        jenkinsRule.getInstance().proxy = proxyConfiguration;

        Client.HttpProxy httpProxy = ClientCache.getHttpProxy();

        Assert.assertEquals("Can't read Jenkins proxy configuration name", proxyConfiguration.name, httpProxy.host);
        Assert.assertEquals("Can't read Jenkins proxy configuration port", proxyConfiguration.port, httpProxy.port);
        Assert.assertEquals("Jenkins user credentials not matching", proxyConfiguration.getUserName(), httpProxy.getUser());
        Assert.assertEquals("Can't read Jenkins user credentials", proxyConfiguration.getPassword(), httpProxy.getPwrd());

    }

}
