package com.elasticbox.jenkins.util;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.elasticbox.jenkins.auth.Authentication;
import com.elasticbox.jenkins.auth.TokenAuthentication;
import com.elasticbox.jenkins.auth.TokenCredentialsImpl;
import com.elasticbox.jenkins.auth.UserAndPasswordAuthentication;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.QueryParameter;

import java.util.Collections;
import java.util.List;

public class PluginHelper {


    public static Authentication getAuthenticationData(String credentialsId) {
        if (StringUtils.isBlank(credentialsId) ) {
            return null;
        }
        Authentication authData = null;
        final StandardCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class, Jenkins.get(),
                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList() ),
                CredentialsMatchers.withId(credentialsId) );

        if (TokenCredentialsImpl.class.isInstance(credentials)) {
            TokenCredentialsImpl tokenCredentials = (TokenCredentialsImpl)credentials;
            authData = new TokenAuthentication(tokenCredentials.getSecret().getPlainText() );

        } else if (UsernamePasswordCredentials.class.isInstance(credentials)) {
            UsernamePasswordCredentials userPw = (UsernamePasswordCredentials)credentials;
            authData = new UserAndPasswordAuthentication(userPw.getUsername(),
                    userPw.getPassword().getPlainText() );

        } else if (StringCredentialsImpl.class.isInstance(credentials)) {
            StringCredentialsImpl stringCredentials = (StringCredentialsImpl)credentials;
            authData = new TokenAuthentication(stringCredentials.getSecret().getPlainText() );
        }
        return authData;
    }


    public static ListBoxModel doFillCredentialsIdItems(@QueryParameter String endpointUrl) {

        StandardListBoxModel listBoxModel = (StandardListBoxModel) new StandardListBoxModel();
        listBoxModel.includeEmptyValue();

        // Important! Otherwise you expose credentials metadata to random web requests.
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return listBoxModel;
        }

        CredentialsMatcher matcher = CredentialsMatchers.anyOf(
                CredentialsMatchers.instanceOf(TokenCredentialsImpl.class),
                CredentialsMatchers.instanceOf(StandardCredentials.class));

        List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(endpointUrl).build();

        listBoxModel.includeMatchingAs(
                ACL.SYSTEM,
                Jenkins.get(),
                StandardCredentials.class,
                domainRequirements,
                matcher );

        return listBoxModel;
    }
}