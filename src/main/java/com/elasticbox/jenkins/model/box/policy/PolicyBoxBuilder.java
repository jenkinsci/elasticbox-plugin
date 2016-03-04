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

package com.elasticbox.jenkins.model.box.policy;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.model.profile.ProfileType;
import com.elasticbox.jenkins.model.provider.Provider;

public abstract class PolicyBoxBuilder<B extends AbstractBox.ComplexBuilder<B,T>,T>
    extends AbstractBox.ComplexBuilder<B, T> {

    protected ProfileType profileType;

    private String [] claims;
    private Provider provider;

    public PolicyBoxBuilder() {
        this.type = BoxType.POLICY;
    }

    public abstract B withProfileType(String schema);

    public B withProvider(Provider provider) {
        this.provider = provider;
        return getThis();
    }

    public B withClaims(String [] claims) {
        this.claims = claims;
        return getThis();
    }

    public ProfileType getProfileType() {
        return profileType;
    }

    public String[] getClaims() {
        return claims;
    }

    public Provider getProvider() {
        return provider;
    }
}
