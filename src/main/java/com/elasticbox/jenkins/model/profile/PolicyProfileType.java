package com.elasticbox.jenkins.model.profile;

import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.provider.ProviderType;

import java.util.logging.Logger;

/**
 * Created by serna on 11/26/15.
 */
public enum PolicyProfileType implements ProfileType {

    AMAZON_CLOUDFORMATION("/aws/cloudformation/profile"){
        @Override
        public ProviderType provider() {
            return ProviderType.AMAZON;
        }
    },
    AMAZON_EC2("/aws/ec2/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.AMAZON;
        }
    },
    AMAZON_ECS("/aws/ecs/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.AMAZON;
        }
    },
    AZURE_LINUX("/azure/compute/linux/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.AZURE;
        }
    },
    AZURE_WINDOWS("/azure/compute/windows/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.AZURE;
        }
    },
    BYOI("/byoi/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.BYOI;
        }
    },
    CLOUDSTACK("/cloudstack/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.CLOUDSTACK;
        }
    },
    DIMENSION_DATA("/dimension-data/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.DIMENSION_DATA;
        }
    },
    GCE("/gce/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.GCE;
        }
    },
    OPENSTACK("/openstack/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.OPENSTACK;
        }
    },
    SOFTLAYER("/softlayer/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.SOFTLAYER;
        }
    },
    VCLOUD("/vcloud/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.VCLOUD;
        }
    },
    VSPHERE("/vsphere/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.VSPHERE;
        }
    },
    TEST("/test/compute/profile") {
        @Override
        public ProviderType provider() {
            return ProviderType.TEST;
        }
    },
    UNKNOWN("/unknown/compute/profile"){
        @Override
        public ProviderType provider() {
            return ProviderType.UNKNOWN;
        }
    };

    private static final Logger logger = Logger.getLogger(PolicyProfileType.class.getName());

    private final String schema;

    PolicyProfileType(String schema) {
        this.schema = schema;
    }

    @Override
    public boolean isType(String schema){
        return schema.endsWith(this.schema);
    }

    @Override
    public String getSchema() {
        return schema;
    }

    public static PolicyProfileType getType(String schema) {
        PolicyProfileType[] values = PolicyProfileType.values();
        for (PolicyProfileType type : values) {
            if(type.isType(schema))
                return type;
        }

        logger.warning("There is no profile type acording to this schema: "+schema);

        return UNKNOWN;
    }


}