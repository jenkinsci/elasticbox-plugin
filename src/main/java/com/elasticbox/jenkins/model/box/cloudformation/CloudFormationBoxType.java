package com.elasticbox.jenkins.model.box.cloudformation;

import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang.ArrayUtils;

public enum CloudFormationBoxType {

    MANAGED {
        @Override
        public String[] getIncludedTypes() {
            return new String[]{
                "MySQL Database Service",
                "Microsoft SQL Database Service",
                "Oracle Database Service",
                "PostgreSQL Database Service",
                "Memcached Service",
                "S3 Bucket",
                "Dynamo DB Domain"
            };
        }
    },
    TEMPLATE {
        @Override
        public String[] getIncludedTypes() {
            return new String[]{"CloudFormation Service"};
        }

    };

    public abstract String[] getIncludedTypes();

    public static CloudFormationBoxType getType(String type) throws ElasticBoxModelException {
        CloudFormationBoxType[] values = CloudFormationBoxType.values();
        for (CloudFormationBoxType cloudFormationBoxType : values) {
            if ( ArrayUtils.contains(cloudFormationBoxType.getIncludedTypes(), type) ) {
                return cloudFormationBoxType;
            }
        }
        throw new ElasticBoxModelException("There is no cloud formation box with type : " + type);
    }

}
