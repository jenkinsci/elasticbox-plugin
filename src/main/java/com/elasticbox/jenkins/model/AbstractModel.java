package com.elasticbox.jenkins.model;

/**
 * Created by serna on 11/27/15.
 */
public class AbstractModel {

    private String id;

    public AbstractModel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}
