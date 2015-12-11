package com.elasticbox.jenkins.repository.api.criteria;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.repository.api.factory.box.BoxFactory;
import com.elasticbox.jenkins.repository.api.factory.box.PolicyBoxFactory;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/26/15.
 */
public class NoCloudFormationPolicyBoxesJSONCriteria extends BoxJSONCriteria<PolicyBox> {

    public NoCloudFormationPolicyBoxesJSONCriteria(BoxFactory<PolicyBox> factory) {
        super(factory);
    }

    public NoCloudFormationPolicyBoxesJSONCriteria() {
        super(new PolicyBoxFactory());
    }

    @Override
    boolean performFit(JSONObject jsonObject) {

        String schema = jsonObject.getString("schema");

        if(BoxType.POLICY.isType(schema)){
            String policySchema = jsonObject.getJSONObject("profile").getString("schema");

            if(PolicyProfileType.AMAZON_CLOUDFORMATION.isType(policySchema))
                return false;

            return true;
        }

        return false;
    }


}