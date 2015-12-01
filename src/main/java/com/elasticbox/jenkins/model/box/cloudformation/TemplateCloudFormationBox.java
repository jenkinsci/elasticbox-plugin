package com.elasticbox.jenkins.model.box.cloudformation;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.ClaimsVsRequirementsDeployable;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Created by serna on 11/29/15.
 */
public class TemplateCloudFormationBox extends AbstractBox implements CloudFormationBox, ClaimsVsRequirementsDeployable{

    private String [] requirements;

    private TemplateCloudFormationBox(String id, String name, String [] requirements) {
        super(id, name, BoxType.CLOUDFORMATION);
        this.requirements = requirements;
    }

    public CloudFormationBoxType getCloudFormationType() {
        return CloudFormationBoxType.TEMPLATE;
    }

    @Override
    public boolean isManagedCloudFormationBox() {
        return false;
    }

    @Override
    public boolean isTemplateCloudFormationBox() {
        return true;
    }

    @Override
    public String[] getRequirements() {
        return requirements;
    }

    public static class ComplexBuilder {

        private String newId;
        private String newName;
        private String [] newRequirements;

        public NameBuilder withId( String id ){
            newId = id;
            return new NameBuilder();
        }

        public class NameBuilder {
            private NameBuilder() {}
            public CloudFormationBuilder withName( String name ) {
                newName = name;
                return new CloudFormationBuilder();
            }
        }

        public class CloudFormationBuilder {
            private CloudFormationBuilder() {}

            public CloudFormationBuilder withRequirements(String [] requirements){
                newRequirements = requirements;
                return  this;
            }

            public TemplateCloudFormationBox build() throws ElasticBoxModelException {
                if (StringUtils.isNotEmpty(newId) &&
                        StringUtils.isNotEmpty(newName)){
                    return new TemplateCloudFormationBox(newId, newName, newRequirements);
                }

                throw new ElasticBoxModelException("Not valid parameters for building Template CloudFormation box");
            }
        }

    }

}
