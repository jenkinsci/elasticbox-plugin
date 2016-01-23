package com.elasticbox.jenkins.model.box.cloudformation;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang.StringUtils;

import java.util.List;

/**
 * Created by serna on 11/29/15.
 */
public class TemplateCloudFormationBox extends AbstractBox implements CloudFormationBox, ClaimsVsRequirementsDeployable{

    private String [] requirements;

    private TemplateCloudFormationBox(TemplateCloudFormationBoxBuilder builder) {
        super(builder);
        this.requirements = builder.requirements;
    }

    public CloudFormationBoxType getCloudFormationType() {
        return CloudFormationBoxType.TEMPLATE;
    }

    @Override
    public String[] getRequirements() {
        return requirements;
    }


    public static class TemplateCloudFormationBoxBuilder extends AbstractBox.ComplexBuilder<TemplateCloudFormationBoxBuilder, TemplateCloudFormationBox> {

        private String[] requirements;

        public TemplateCloudFormationBoxBuilder() {
            this.type = BoxType.CLOUDFORMATION;
        }

        public TemplateCloudFormationBoxBuilder withRequirements(String[] requirements) {
            this.requirements = requirements;
            return getThis();
        }

        @Override
        public TemplateCloudFormationBox build() {
            return new TemplateCloudFormationBox(this);
        }
    }

}
