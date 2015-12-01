package com.elasticbox.jenkins.model.profile;

import com.elasticbox.jenkins.model.provider.ProviderType;

/**
 * Created by serna on 11/29/15.
 */
public interface ProfileType {

    ProviderType provider();

    boolean isType(String schema);

    String getSchema();
}
