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

package com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.provider.Provider;
import com.elasticbox.jenkins.model.repository.api.deserializer.Utils;
import net.sf.json.JSONObject;

public class PolicyBoxTransformer extends AbstractBoxTransformer<PolicyBox> {

    @Override
    public PolicyBox apply(JSONObject jsonObject) throws ElasticBoxModelException {

        PolicyBox policyBox = new PolicyBox.SimplePolicyBoxBuilder()
                .withOwner(jsonObject.getString("owner"))
                .withClaims(Utils.toStringArray(jsonObject.getJSONArray("claims")))
                .withProfileType(jsonObject.getJSONObject("profile").getString("schema"))
                .withId(jsonObject.getString("id"))
                .withMembers(getMembers(jsonObject.getJSONArray("members")))
                .withName(jsonObject.getString("name"))
                .withProvider(new Provider(jsonObject.getString("provider_id")))
                .build();

        return policyBox;
    }


    @Override
    public boolean shouldApply(JSONObject jsonObject) {
        return super.canCreate(jsonObject, BoxType.POLICY);
    }
}
