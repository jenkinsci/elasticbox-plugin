package com.elasticbox.jenkins.model.profile;

import com.elasticbox.jenkins.model.AbstractModel;

public class Profile {

    private String schema;

    public Profile(String schema) {
        this.schema = schema;
    }

    public String getSchema() {
        return schema;
    }
}
