package com.elasticbox.jenkins.model.box.script;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by serna on 11/26/15.
 */
public class ScriptBox extends AbstractBox implements ClaimsVsRequirementsDeployable {

    private String[] requirements;

    private ScriptBox(ScriptBoxBuilder builder) {
        super(builder);
        this.requirements = builder.requirements;
    }

    public String[] getRequirements() {
        return requirements;
    }

    public static class ScriptBoxBuilder extends ComplexBuilder<ScriptBoxBuilder, ScriptBox> {

        private String[] requirements;

        public ScriptBoxBuilder() {
            this.type = BoxType.SCRIPT;
        }

        public ScriptBoxBuilder withRequirements(String[] requirements) {
            this.requirements = requirements;
            return getThis();
        }

        @Override
        public ScriptBox build() {
            return new ScriptBox(this);
        }
    }
}
