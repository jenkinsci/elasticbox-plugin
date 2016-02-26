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

public class PullRequestInstance {
    public final String id;
    public final String cloud;

    public PullRequestInstance(String id, String cloud) {
        assert id != null & cloud != null;
        this.id = id;
        this.cloud = cloud;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            return true;
        }
        if (obj instanceof PullRequestInstance) {
            PullRequestInstance instance = (PullRequestInstance) obj;
            return id.equals(instance.id) && cloud.equals(instance.cloud);
        }
        return false;
    }

}
