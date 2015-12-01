package com.elasticbox.jenkins.model.profile;

import com.elasticbox.jenkins.model.AbstractModel;

/**
 * Created by serna on 11/26/15.
 */
public class Profile {

    private String schema;

    public Profile(String schema) {
        this.schema = schema;
    }

    public String getSchema() {
        return schema;
    }
}
