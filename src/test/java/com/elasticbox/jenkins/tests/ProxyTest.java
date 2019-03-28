package com.elasticbox.jenkins.tests;

import com.elasticbox.Client;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.ProxyConfiguration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.logging.Logger;


public class ProxyTest {
    private final Logger LOGGER = Logger.getLogger(ProxyTest.class.getName());

    private final String TEST_JENKINS_PROXY_NAME = "http://21.22.23.24";
    private final int TEST_JENKINS_PROXY_PORT = Integer.parseInt("25");
    private final String TEST_JENKINS_PROXY_USER = "proxy_user";
    private final String TEST_JENKINS_PROXY_PWRD = "1234";


    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testProxyConfigured() throws Exception {

        ProxyConfiguration proxyConfiguration = new ProxyConfiguration (TEST_JENKINS_PROXY_NAME, TEST_JENKINS_PROXY_PORT,
                TEST_JENKINS_PROXY_USER, TEST_JENKINS_PROXY_PWRD);

        jenkinsRule.getInstance().proxy = proxyConfiguration;

        Client.HttpProxy httpProxy = ClientCache.getJenkinsHttpProxyCfg();

        Assert.assertEquals("Can't read Jenkins proxy configuration name", proxyConfiguration.name, httpProxy.host);
        Assert.assertEquals("Can't read Jenkins proxy configuration port", proxyConfiguration.port, httpProxy.port);
        Assert.assertEquals("Jenkins user credentials not matching", proxyConfiguration.getUserName(), httpProxy.getUser());
        Assert.assertEquals("Can't read Jenkins user credentials", proxyConfiguration.getPassword(), httpProxy.getPwrd());
    }
}
