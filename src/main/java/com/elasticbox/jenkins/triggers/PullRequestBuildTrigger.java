/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.triggers;

import com.elasticbox.jenkins.triggers.github.WebHook;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.RootAction;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.io.IOException;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestBuildTrigger extends Trigger<AbstractProject<?, ?>> {
    private static final Logger LOGGER = Logger.getLogger(PullRequestBuildTrigger.class.getName());
    
    private final String triggerPhrase;
    private final String whitelist;
    
    private transient IBuildHandler buildHandler;
    
    @DataBoundConstructor
    public PullRequestBuildTrigger(String triggerPhrase, String whitelist) {
        this.triggerPhrase = triggerPhrase;
        this.whitelist = whitelist;
    }

    public String getTriggerPhrase() {
        return triggerPhrase;
    }    

    public String getWhitelist() {
        return whitelist;
    }

    public IBuildHandler getBuildHandler() {
        return buildHandler;
    }        

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        if (!getDescriptor().isGitHubPluginInstalled()) {
            LOGGER.severe(MessageFormat.format("{0} requires GitHub plugin.", getDescriptor().getDisplayName()));
            return;
        }
        
        super.start(project, newInstance);
        try {
            buildHandler = getDescriptor().getBuildManager().createBuildHandler(project, newInstance);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }    
    
    @Extension
    public static final class DescriptorImpl extends TriggerDescriptor {
        private String webHookExternalUrl;

        private transient BuildManager<?> buildManager;
        
        public DescriptorImpl() {
            load();            
        }
        
        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox GitHub Pull Request Lifecycle Management";
        }

        @Override
        public Trigger<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (!isGitHubPluginInstalled()) {
                throw new FormException(MessageFormat.format("{0} requires GitHub plugin.", getDisplayName()), "all");
            }
            return super.newInstance(req, formData);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            webHookExternalUrl = json.getString("webHookExternalUrl").trim();
            save();
            return true;
        }                
        
        public boolean isGitHubPluginInstalled() {
            return Jenkins.getInstance().getDescriptor("com.cloudbees.jenkins.GitHubPushTrigger") != null;
        }

        public BuildManager<?> getBuildManager() {
            if (buildManager == null) {
                ExtensionList<BuildManager> buildManagers = Jenkins.getInstance().getExtensionList(BuildManager.class);
                if (!buildManagers.isEmpty()) {
                    buildManager = buildManagers.get(0);
                } 
            }
            return buildManager;
        }
        
        public String getWebHookUrl() {
            String jenkinsUrl = Jenkins.getInstance().getRootUrl();
            if (!jenkinsUrl.endsWith("/")) {
                jenkinsUrl += '/';
            }
            return jenkinsUrl + Jenkins.getInstance().getExtensionList(RootAction.class).get(WebHook.class).getUrlName() + '/';
        }

        public String getWebHookExternalUrl() {
            return webHookExternalUrl;
        }
                
    }
}
