package com.elasticbox.jenkins.model;

public class AbstractModel {

    private String id;

    public AbstractModel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}
