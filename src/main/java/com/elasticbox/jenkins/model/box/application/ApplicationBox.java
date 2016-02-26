package com.elasticbox.jenkins.model.box.application;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.model.profile.ProfileType;
import com.elasticbox.jenkins.model.provider.Provider;
import org.apache.commons.lang.StringUtils;

import java.util.List;

public class ApplicationBox extends AbstractBox {

    public ApplicationBox(ApplicationBoxBuilder builder) {
        super(builder);
    }

    public static class ApplicationBoxBuilder extends AbstractBox.ComplexBuilder<ApplicationBoxBuilder,ApplicationBox> {

        public ApplicationBoxBuilder() {
            this.type = BoxType.APPLICATION;
        }

        @Override
        public ApplicationBox build() {
            return new ApplicationBox(this);
        }
    }
}
