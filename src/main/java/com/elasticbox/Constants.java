/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Phong Nguyen Le
 */
public interface Constants {

    String UTF_8 = "UTF-8";
    String BASE_ELASTICBOX_SCHEMA = "http://elasticbox.net/schemas/";
    String ELASTICBOX_RELEASE = "4.0";


    //---------- Validation deployment data
    String AT_LEAST_SELECT_POLICY_OR_REQUIREMENTS = "Policy box or requirements to find one are mandatory";
    String PROVIDER_SHOULD_BE_PROVIDED = "Provider are mandatory";
    String LOCATION_SHOULD_BE_PROVIDED = "Location are mandatory";

    //---------- Boxes
    String BOX_ANY_BOX = "AnyBox";
    String BOX_LATEST_BOX_VERSION = "LATEST";

    Map<String, String> SERVICES_BOXES_TO_BE_EXCLUDED = new HashMap<String, String>(){{
        put("MySQL Database Service", "");
        put("Microsoft SQL Database Service", "");
        put("Oracle Database Service", "");
        put("PostgreSQL Database Service", "");
        put("S3 Bucket", "");
        put("Dynamo DB Domain","");
        put("Memcached Service","");
        put("CloudFormation Service","");
        put("Container Service","");
        put("Docker Container", "");
    }};

    //-----------Instances
    String INSTANCE_ACTION_NONE = "none";
    String INSTANCE_ACTION_SKIP = "skip";
    String INSTANCE_ACTION_RECONFIGURE = Client.InstanceOperation.RECONFIGURE;
    String INSTANCE_ACTION_REINSTALL = Client.InstanceOperation.REINSTALL;
    String INSTANCE_ACTION_DELETE_AND_DEPLOY = "deleteAndDeploy";
    String INSTANCES_PAGE_URL_PATTERN = "{0}/#/instances/{1}/{2}";
    String INSTANCES_API_RESOURCE = "/services/instances";

    String DEPLOYMENT_REQUEST_SCHEMA_NAME = "deploy-instance-request";
    String DEPLOYMENT_APPLICATION_REQUEST_SCHEMA_NAME = "deploy/application";

    long DEFAULT_DEPLOYMENT_APPLICATION_BOX_TIMEOUT = 3700;

    String LATEST_BOX_VERSION = "LATEST";
    String ANY_BOX = "AnyBox";

    String AUTOMATIC_UPDATES_OFF = "off";
    String AUTOMATIC_UPDATES_MAJOR = "major";
    String AUTOMATIC_UPDATES_MINOR = "minor";
    String AUTOMATIC_UPDATES_PATCH = "patch";

    //-----------Provider
    String AMAZON_PROVIDER_TYPE = "Amazon Web Services";
    String WINDOWS_CLAIM = "windows";
    String LINUX_CLAIM = "linux";
    String CLOUD_FOUNDATION_SERVICE = "CloudFormation Service";

    String BINDING_TYPE_VARIABLE = "Binding";
    String PRIVATE_VISIBILITY = "private";

    //-----------Log
    String LOG_PREFIX = "[ElasticBox] - ";
}
