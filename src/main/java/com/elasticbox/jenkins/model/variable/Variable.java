/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.variable;

public class Variable {

    private Type type;
    private Visibility visibility;

    private boolean required;
    private String name;
    private String value;

    public Variable(Type type, Visibility visibility, boolean required, String name, String value) {
        this.type = type;
        this.visibility = visibility;
        this.required = required;
        this.name = name;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public boolean isRequired() {
        return required;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public enum Type {
        TEXT, BINDING, PORT, BOX, FILE, OPTIONS, PASSWORD, NUMBER
    }

    public enum Visibility {
        PUBLIC, PRIVATE, INTERNAL
    }

}
