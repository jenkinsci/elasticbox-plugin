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

import com.elasticbox.jenkins.util.ProjectData;

import java.util.ArrayList;
import java.util.List;

public class PullRequests extends ProjectData.Datum {
    private final List<PullRequestData> data;

    public PullRequests() {
        this.data = new ArrayList<PullRequestData>();
    }

    @Override
    protected void setProjectData(ProjectData projectData) {
        if (data != null) {
            for (PullRequestData pullRequestData : data) {
                pullRequestData.setProjectData(projectData);
            }
        }
    }

    public List<PullRequestData> getData() {
        return data;
    }

}
