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

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.repository.api.criteria.AbstractJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.factory.box.BoxFactory;
import com.elasticbox.jenkins.model.repository.api.factory.instance.InstanceFactory;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/27/15.
 */
public class InstanceJSONCriteria<T> extends AbstractJSONCriteria<T> {

    public InstanceJSONCriteria(InstanceFactory<T> factory) {
        super(factory);
    }

    @Override
    protected boolean fits(JSONObject jsonObject) {

        return jsonObject.getString("schema").equals("http://elasticbox.net/schemas/instance");

    }


}
