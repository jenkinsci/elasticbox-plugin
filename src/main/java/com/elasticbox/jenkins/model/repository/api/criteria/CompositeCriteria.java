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

package com.elasticbox.jenkins.model.repository.api.criteria;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.repository.api.factory.ModelFactory;
import net.sf.json.JSONObject;

import java.util.List;

/**
 * Created by serna on 1/8/16.
 */
public class CompositeCriteria<T> extends AbstractJSONCriteria<T> {

    private final List<AbstractJSONCriteria> criterias;

    public CompositeCriteria(ModelFactory<T> factory, List<AbstractJSONCriteria> criterias) {
        super(factory);
        this.criterias = criterias;
    }

    @Override
    protected boolean fits(JSONObject jsonObject) {

        for (AbstractJSONCriteria criteria : criterias) {
            if (!criteria.fits(jsonObject)) {
                return false;
            }
        }

        return true;
    }

}
