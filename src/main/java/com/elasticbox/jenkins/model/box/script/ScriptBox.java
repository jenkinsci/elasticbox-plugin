package com.elasticbox.jenkins.model.box.script;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by serna on 11/26/15.
 */
public class ScriptBox extends AbstractBox implements ClaimsVsRequirementsDeployable {

    private String[] requirements;

    private ScriptBox(String id, String name, String[] requirements) {
        super(id, name, BoxType.SCRIPT);
        this.requirements = requirements;
    }

    public String[] getRequirements() {
        return requirements;
    }

    public static class ComplexBuilder {

        private String newId;
        private String newName;
        private String[] newRequirements;

        public ComplexBuilder() {
        }

        public NameBuilder withId(String id) {
            newId = id;
            return new NameBuilder();
        }

        public class NameBuilder {
            private NameBuilder() {
            }

            public BoxBuilder withName(String name) {
                newName = name;
                return new BoxBuilder();
            }
        }

        public class BoxBuilder {
            private BoxBuilder() {
            }

            public BoxBuilder withRequirements(String[] requirements) {
                newRequirements = requirements;
                return this;
            }

            public ScriptBox build() throws ElasticBoxModelException {
                if (StringUtils.isNotEmpty(newId) &&
                        StringUtils.isNotEmpty(newName)) {
                    return new ScriptBox(newId, newName, newRequirements);
                }

                throw new ElasticBoxModelException("Not valid parameters for building Box");
            }
        }
    }
}
