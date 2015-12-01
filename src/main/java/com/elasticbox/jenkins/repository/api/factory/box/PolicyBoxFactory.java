package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.repository.api.factory.JSONFactoryUtils;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class PolicyBoxFactory implements IBoxFactory<PolicyBox>{

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
        final String schema = jsonObject.getString("schema");
        if(StringUtils.isNotBlank(schema) && BoxType.isBox(schema)){
            try {
                final BoxType boxType  = BoxType.geType(schema);
                return boxType == BoxType.POLICY;
            } catch (ElasticBoxModelException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
