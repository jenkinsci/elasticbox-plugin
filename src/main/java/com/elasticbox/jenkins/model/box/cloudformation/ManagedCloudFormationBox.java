package com.elasticbox.jenkins.model.box.cloudformation;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.profile.ProfileType;
import com.elasticbox.jenkins.model.provider.ProviderType;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class ManagedCloudFormationBox extends PolicyBox implements CloudFormationBox {

    private ManagedCloudFormationBox(String id, String name, ProfileType type) {
        super(id, name, BoxType.CLOUDFORMATION, type);
    }

    public CloudFormationBoxType getCloudFormationType() {
        return CloudFormationBoxType.MANAGED;
    }

    @Override
    public boolean isManagedCloudFormationBox() {
        return true;
    }

    @Override
    public boolean isTemplateCloudFormationBox() {
        return false;
    }

    public enum ManagedCloudFormationProfileType implements ProfileType {

        RDS("/aws/rds/profile"),
        ELASTIC_CACHE("/aws/elasticache/profile"),
        DDB("/aws/ddb/profile"),
        S3("/aws/s3/profile")
        ;

        private String schema;

        ManagedCloudFormationProfileType(String schema) {
            this.schema = schema;
        }

        @Override
        public ProviderType provider() {
            return ProviderType.AMAZON;
        }

        @Override
        public boolean isType(String schema){
            return schema.endsWith(this.schema);
        }

        @Override
        public String getSchema() {
            return schema;
        }

        public static ManagedCloudFormationProfileType geType(String schema) throws ElasticBoxModelException {
            ManagedCloudFormationProfileType[] values = ManagedCloudFormationProfileType.values();
            for (ManagedCloudFormationProfileType type : values) {
                if(type.isType(schema))
                    return type;
            }
            throw new ElasticBoxModelException("There is no profile type acording to this schema: "+schema);
        }

    }

    public static class ComplexBuilder {

        private ManagedCloudFormationProfileType managedCloudFormationType;
        private String newId;
        private String newName;

        public IdBuilder withManagedCloudFormationType( String profileSchema ){
            try {
                managedCloudFormationType = ManagedCloudFormationProfileType.geType(profileSchema);
            } catch (ElasticBoxModelException e) {
                e.printStackTrace();
            }
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
            public CloudFormationBuilder withName( String name ) {
                newName = name;
                return new CloudFormationBuilder();
            }
        }

        public class CloudFormationBuilder {
            private CloudFormationBuilder() {}

            public ManagedCloudFormationBox build() throws ElasticBoxModelException {
                if (managedCloudFormationType!=null &&
                        StringUtils.isNotEmpty(newId) &&
                        StringUtils.isNotEmpty(newName)){
                    return new ManagedCloudFormationBox(newId, newName, managedCloudFormationType);
                }

                throw new ElasticBoxModelException("Not valid parameters for building CloudFormation box");
            }
        }

    }

}
