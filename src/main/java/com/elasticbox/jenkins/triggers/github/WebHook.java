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

package com.elasticbox.jenkins.triggers.github;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class WebHook implements UnprotectedRootAction {
    private static final Logger LOGGER = Logger.getLogger(WebHook.class.getName());
    
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "elasticbox";
    }

    @RequirePOST
    public void doIndex(StaplerRequest req, StaplerResponse rsp) {
        // handle request from GitHub
        String event = req.getHeader("X-GitHub-Event");
        if (event != null) {
            String payload = req.getParameter("payload");
            LOGGER.finest(MessageFormat.format("GitHub event: {0}", event));
            try {
                PullRequestManager.getInstance().handleEvent(event, payload);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error handling GitHub event: {0}", event), ex);
                LOGGER.finest(MessageFormat.format("Event payload: {0}", payload));
            }
        }
    }
    
}
