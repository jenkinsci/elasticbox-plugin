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

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        BuildManager buildManager = getDescriptor().getBuildManager();

        if (buildManager == null) {

            LOGGER.severe(
                MessageFormat.format(
                    "Cannot retrieve build manager. {0} requires GitHub plugin, you need to install GitHub plugin"
                        + " in order to use it.",
                    getDescriptor().getDisplayName())
            );

            return;
        }

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
            return "Manage GitHub pull request lifecycle with ElasticBox";
        }

        @Override
        public Trigger<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {

            if (getBuildManager() == null) {
                throw new FormException(
                    MessageFormat.format(
                        "Cannot retrieve build manager. ''{0}'' requires GitHub plugin, you need to install GitHub "
                            + "plugin in order to use it.",
                        getDisplayName()),
                    "all");
            }

            return super.newInstance(req, formData);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            webHookExternalUrl = json.getString("webHookExternalUrl").trim();
            save();
            return true;
        }

        public BuildManager<?> getBuildManager() {
            if (buildManager == null) {
                ExtensionList<BuildManager> buildManagers = Jenkins.get().getExtensionList(BuildManager.class);
                if (!buildManagers.isEmpty()) {
                    buildManager = buildManagers.get(0);
                }
            }
            return buildManager;
        }

        public String getWebHookUrl() {
            String jenkinsUrl = Jenkins.get().getRootUrl();
            if (jenkinsUrl == null) {
                LOGGER.severe("Jenkins URL is not configured");
                return null;
            }

            if (!jenkinsUrl.endsWith("/")) {
                jenkinsUrl += '/';
            }
            return jenkinsUrl
                + Jenkins.get().getExtensionList(RootAction.class).get(WebHook.class).getUrlName() + '/';
        }

        public String getWebHookExternalUrl() {
            return webHookExternalUrl;
        }

        public void setWebHookExternalUrl(String webHookExternalUrl) {
            this.webHookExternalUrl = webHookExternalUrl;
        }

    }
}
