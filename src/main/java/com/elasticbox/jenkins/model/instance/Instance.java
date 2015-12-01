package com.elasticbox.jenkins.model.instance;

import com.elasticbox.jenkins.model.AbstractModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/27/15.
 */
public class Instance extends AbstractModel {

    private String name;
    private List<String> tags;

    public Instance(String id, String name) {
        super(id);
        this.tags = new ArrayList<String>();
        this.name = name;
    }

    public void addTag(String tag){
        this.tags.add(tag);
    }

    public String [] getgetTags() {
        String[] tagsArray = tags.toArray(new String[tags.size()]);
        return tagsArray;
    }

    public String getName() {
        return name;
    }
}
