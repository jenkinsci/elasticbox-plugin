package com.elasticbox.jenkins.model.box.cloudformation;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBoxBuilder;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.profile.ProfileType;
import com.elasticbox.jenkins.model.provider.ProviderType;

public class ManagedCloudFormationBox extends PolicyBox implements CloudFormationBox {

    private ManagedCloudFormationBox(ManagedCloudFormationPolicyBoxBuilder builder) {
        super(builder);

    }

    public CloudFormationBoxType getCloudFormationType() {
        return CloudFormationBoxType.MANAGED;
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
        public boolean isType(String schema) {
            return schema.endsWith(this.schema);
        }

        @Override
        public String getSchema() {
            return schema;
        }

        public static ManagedCloudFormationProfileType getType(String schema) throws ElasticBoxModelException {
            ManagedCloudFormationProfileType[] values = ManagedCloudFormationProfileType.values();
            for (ManagedCloudFormationProfileType type : values) {
                if (type.isType(schema)) {
                    return type;
                }
            }
            throw new ElasticBoxModelException("There is no profile type acording to this schema: " + schema);
        }

    }

    public static class ManagedCloudFormationPolicyBoxBuilder
        extends PolicyBoxBuilder<ManagedCloudFormationPolicyBoxBuilder,ManagedCloudFormationBox> {


        public ManagedCloudFormationPolicyBoxBuilder() {
            this.type = BoxType.CLOUDFORMATION;
        }

        @Override
        public ManagedCloudFormationPolicyBoxBuilder withProfileType(String profileSchema) {
            this.profileType = ManagedCloudFormationProfileType.getType(profileSchema);
            return getThis();
        }

        @Override
        public ManagedCloudFormationBox build() {
            return new ManagedCloudFormationBox(this);
        }

    }


}
