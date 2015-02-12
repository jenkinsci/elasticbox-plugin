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

package com.elasticbox.jenkins.tests;

import java.io.IOException;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
public class TestBoxData {
    public final String jsonFileName;
    public final String profileId;
    public final String boxId;
    private JSONObject json;
    private String newProfileId;

    public TestBoxData(String jsonFileName, String profileId) {
        this.jsonFileName = jsonFileName;
        this.profileId = this.newProfileId = profileId;
        try {
            json = JSONObject.fromObject(TestUtils.getResourceAsString(jsonFileName));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        boxId = json.getString("id");
    }

    public JSONObject getJson() throws IOException {
        if (json == null) {
            json = JSONObject.fromObject(TestUtils.getResourceAsString(jsonFileName));
        }
        return json;
    }

    public void setJson(JSONObject json) {
        this.json = json;
    }

    public String getNewProfileId() {
        return newProfileId;
    }

    public void setNewProfileId(String newProfileId) {
        this.newProfileId = newProfileId;
    }
    
}
