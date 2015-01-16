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

import hudson.model.AbstractProject;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.kohsuke.github.GHPullRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class PullRequestData {
    public static class Instance {
        public final String id;
        public final String cloud;

        public Instance(String id, String cloud) {
            assert id != null & cloud != null;
            this.id = id;
            this.cloud = cloud;
        }    

        @Override
        public boolean equals(Object obj) {
            if (super.equals(obj)) {
                return true;
            }
            if (obj instanceof Instance) {
                Instance instance = (Instance) obj;
                return id.equals(instance.id) && cloud.equals(instance.cloud);
            }
            return false;
        }                
        
    }
    
    public final URL pullRequestUrl;   
    public final String projectFullName;
    private Date lastUpdated;
    private String headSha;
    private final List<Instance> instances;

    public PullRequestData(GHPullRequest pullRequest, AbstractProject project) {
        this.pullRequestUrl = pullRequest.getUrl();
        this.projectFullName = project.getFullName();
        this.headSha = pullRequest.getHead().getSha();
        this.lastUpdated = pullRequest.getUpdatedAt();
        this.instances = new ArrayList<Instance>();
    }     

    public Date getLastUpdated() {
        return lastUpdated;
    }        

    public String getHeadSha() {
        return headSha;
    }        

    public List<Instance> getInstances() {
        return instances;
    }
        
    public boolean update(GHPullRequest pullRequest) {
        if (!pullRequest.getUrl().equals(pullRequestUrl)) {
            return false;
        }
        if (pullRequest.getUpdatedAt().compareTo(lastUpdated) <= 0) {
            return false;
        }
        lastUpdated = pullRequest.getUpdatedAt();
        headSha = pullRequest.getHead().getSha();
        return !pullRequest.getHead().getSha().equals(headSha);
    }
    
}
