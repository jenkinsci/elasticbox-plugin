package com.elasticbox.jenkins.model.profile;

import com.elasticbox.jenkins.model.provider.ProviderType;

public interface ProfileType {

    ProviderType provider();

    boolean isType(String schema);

    String getSchema();
}
