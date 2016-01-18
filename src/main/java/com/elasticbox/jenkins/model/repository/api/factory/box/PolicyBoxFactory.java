package com.elasticbox.jenkins.model.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.api.factory.JSONFactoryUtils;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public class PolicyBoxFactory extends AbstractBoxFactory<PolicyBox>{

    @Override
    public PolicyBox create(JSONObject jsonObject) throws ElasticBoxModelException {

        PolicyBox policyBox = new PolicyBox.ComplexBuilder()
                .withProfileType(jsonObject.getJSONObject("profile").getString("schema"))
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .withClaims(JSONFactoryUtils.toStringArray(jsonObject.getJSONArray("claims")))
                .build();

        return policyBox;
    }


    @Override
    public boolean canCreate(JSONObject jsonObject) {
        return super.canCreate(jsonObject, BoxType.POLICY);
    }
}
