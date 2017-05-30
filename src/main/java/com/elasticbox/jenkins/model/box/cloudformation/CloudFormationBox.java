package com.elasticbox.jenkins.model.box.cloudformation;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;

public class CloudFormationBox extends AbstractBox implements ClaimsVsRequirementsDeployable {

    public static final String CLOUD_FORMATION_TYPE = "CloudFormation Service";
    private String [] requirements;

    private CloudFormationBox(CloudFormationBoxBuilder builder) {
        super(builder);
        this.requirements = builder.requirements;
    }

    public String getCloudFormationType() {
        return CLOUD_FORMATION_TYPE;
    }

    @Override
    public String[] getRequirements() {
        return requirements;
    }


    public static class CloudFormationBoxBuilder
        extends AbstractBox.ComplexBuilder<CloudFormationBoxBuilder, CloudFormationBox> {

        private String[] requirements;

        public CloudFormationBoxBuilder() {
            this.type = BoxType.CLOUDFORMATION;
        }

        public CloudFormationBoxBuilder withRequirements(String[] requirements) {
            this.requirements = requirements;
            return getThis();
        }

        @Override
        public CloudFormationBox build() {
            return new CloudFormationBox(this);
        }
    }

}
