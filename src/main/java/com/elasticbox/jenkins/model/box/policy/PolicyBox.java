package com.elasticbox.jenkins.model.box.policy;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.model.profile.ProfileType;
import com.elasticbox.jenkins.model.provider.Provider;
import org.apache.commons.lang.StringUtils;

/**
 * Created by serna on 11/26/15.
 */
public class PolicyBox extends AbstractBox {

    private ProfileType profileType;

    private String [] claims;

    private Provider provider;

    public PolicyBox(PolicyBoxBuilder builder) {
        super(builder);
        this.claims = builder.getClaims();
        this.profileType = builder.getProfileType();
        this.provider = builder.getProvider();
    }

    public ProfileType getProfileType() {
        return profileType;
    }

    public String [] getClaims() {
        return claims;
    }

    public Provider getProvider() {
        return provider;
    }


    public static class SimplePolicyBoxBuilder extends PolicyBoxBuilder<SimplePolicyBoxBuilder,PolicyBox> {

        @Override
        public SimplePolicyBoxBuilder withProfileType(String schema) {
            this.profileType = PolicyProfileType.getType(schema);;
            return getThis();
        }

        @Override
        public PolicyBox build() {
            return new PolicyBox(this);
        }
    }

}
