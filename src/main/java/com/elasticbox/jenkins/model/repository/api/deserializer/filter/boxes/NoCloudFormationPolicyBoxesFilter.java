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

package com.elasticbox.jenkins.model.repository.api.deserializer.filter.boxes;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.model.repository.api.deserializer.filter.Filter;
import net.sf.json.JSONObject;

public class NoCloudFormationPolicyBoxesFilter implements Filter<JSONObject> {

    @Override
    public boolean apply(JSONObject jsonObject) {
        String schema = jsonObject.getString("schema");

        if (BoxType.POLICY.isType(schema)) {
            String policySchema = jsonObject.getJSONObject("profile").getString("schema");

            if (PolicyProfileType.AMAZON_CLOUDFORMATION.isType(policySchema)) {
                return false;
            }

            return true;
        }

        return false;
    }
}