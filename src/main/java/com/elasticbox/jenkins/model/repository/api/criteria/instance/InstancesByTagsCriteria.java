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

package com.elasticbox.jenkins.model.repository.api.criteria.instance;

import com.elasticbox.Client;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.criteria.AbstractJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.factory.ModelFactory;
import net.sf.json.JSONObject;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by serna on 1/8/16.
 */
public class InstancesByTagsCriteria extends AbstractJSONCriteria<Instance> {

    final Set<String> tags;
    final List<Pattern> tagPatterns;
    final boolean excludeInaccessible;

    public InstancesByTagsCriteria(Set<String> tags, boolean excludeInaccessible) {
        //TODO replace null by a factory to build Instances from JSON
        this(tags, excludeInaccessible, null);
    }


    public InstancesByTagsCriteria(Set<String> tags, boolean excludeInaccessible, ModelFactory<Instance> factory) {
        super(factory);
        this.tags = new HashSet<String>();
        Set<String> regExTags = new HashSet<String>();
        for (String tag : tags) {
            if (tag.startsWith("/") && tag.endsWith("/")) {
                regExTags.add(tag.substring(1, tag.length() - 1));
            } else {
                this.tags.add(tag);
            }
        }
        tagPatterns = new ArrayList<Pattern>();
        for (String tag : regExTags) {
            tagPatterns.add(Pattern.compile(tag));
        }
        this.excludeInaccessible = excludeInaccessible;
    }

    public boolean fits(JSONObject instance) {
        if (tags.isEmpty() && tagPatterns.isEmpty()) {
            return false;
        }

        Set<String> instanceTags = new HashSet<String>(Arrays.asList((String[]) instance.getJSONArray("tags").toArray(new String[0])));
        instanceTags.add(instance.getString("id"));
        boolean hasTags = instanceTags.containsAll(tags);
        if (!hasTags) {
            return false;
        }
        for (Pattern pattern : tagPatterns) {
            boolean matchFound = false;
            for (String tag : instanceTags) {
                if (pattern.matcher(tag).matches()) {
                    matchFound = true;
                    break;
                }
            }
            if (!matchFound) {
                return false;
            }
        }
        if (hasTags && excludeInaccessible) {
            return !Client.InstanceState.UNAVAILABLE.equals(instance.getString("state")) &&
                    !Client.TERMINATE_OPERATIONS.contains(instance.getJSONObject("operation").getString("event"));
        }
        return hasTags;
    }
}