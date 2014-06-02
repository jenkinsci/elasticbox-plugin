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

package com.elasticbox.jenkins;

import hudson.Extension;
import hudson.model.LabelFinder;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author Phong Nguyen Le
 */
@Extension
public class ElasticBoxLabelFinder extends LabelFinder {
    public static final String SINGLE_USE_PREFIX = "elasticbox-single-use-";
    public static final String REUSE_PREFIX = "elasticbox-reuse-";
    public static final ElasticBoxLabelFinder INSTANCE = new ElasticBoxLabelFinder();

    public static final LabelAtom getLabel(String profileId, boolean singleUse) {
        String pattern = singleUse ? SINGLE_USE_PREFIX + "{0}" : REUSE_PREFIX + "{0}";
        return new LabelAtom(MessageFormat.format(pattern, profileId));
    }

    @Override
    public Collection<LabelAtom> findLabels(Node node) {
        if (node instanceof ElasticBoxSlave) {
            ElasticBoxSlave slave = (ElasticBoxSlave) node;
            if (slave.isSingleUse()) {
                if (slave.getComputer() != null && slave.getComputer().getBuilds().isEmpty()) {
                    return Collections.singleton(getLabel(slave.getProfileId(), true));                    
                }
            } else {
                return Collections.singleton(getLabel(slave.getProfileId(), false));
            }
        }
        
        return Collections.emptyList();
    }
    
}
