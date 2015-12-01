package com.elasticbox.jenkins.model.box.policy;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.model.profile.ProfileType;
import com.elasticbox.jenkins.model.provider.Provider;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/26/15.
 */
public class PolicyBox extends AbstractBox {

    private ProfileType profileType;

    private String [] claims;

    private Provider provider;

    private PolicyBox(String id, String name, ProfileType type, String[] claims) {
        super(id, name, BoxType.POLICY);
        this.claims = claims;
        this.profileType = type;
    }

    protected PolicyBox(String id, String name, BoxType boxType, ProfileType type) {
        super(id, name, boxType);
        this.profileType = type;
    }

    public ProfileType getProfile() {
        return profileType;
    }

    public String [] getClaims() {
        return claims;
    }

    public Provider getProvider() {
        return provider;
    }


    public static class ComplexBuilder {

        private PolicyProfileType newPolicyProfileType;
        private String newId;
        private String newName;
        private String [] newClaims;

        public ComplexBuilder() {}

        public IdBuilder withProfileType( String schema ){
            newPolicyProfileType = PolicyProfileType.geType(schema);
            return new IdBuilder();
        }

        public class IdBuilder {
            private IdBuilder() {}
            public NameBuilder withId( String id ) {
                newId = id;
                return new NameBuilder();
            }
        }

        public class NameBuilder {
            private NameBuilder() {}
            public PolicyBoxBuilder withName( String name ) {
                newName = name;
                return new PolicyBoxBuilder();
            }
        }

        public class PolicyBoxBuilder {
            private PolicyBoxBuilder() {}

            public PolicyBoxBuilder withClaims(String [] claims){
                newClaims = claims;
                return  this;
            }

            public PolicyBox build() throws ElasticBoxModelException {
                if (newPolicyProfileType !=null &&
                        StringUtils.isNotEmpty(newId) &&
                            StringUtils.isNotEmpty(newName)){
                                return new PolicyBox(newId, newName, newPolicyProfileType, newClaims);
                }

                throw new ElasticBoxModelException("Not valid parameters for building PolicyBox");
            }
        }
    }



}
