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

/**
 *
 * @author Phong Nguyen Le
 */
public interface Constants {

    //---------- Validation deployment data
    String AT_LEAST_SELECT_POLICY_OR_REQUIREMENTS = "Policy box or requirements to find one are mandatory";
    String PROVIDER_SHOULD_BE_PROVIDED = "Provider are mandatory";
    String LOCATION_SHOULD_BE_PROVIDED = "Location are mandatory";

    //---------- Boxes
    String BOX_ANY_BOX = "AnyBox";
    String BOX_LATEST_BOX_VERSION = "LATEST";

    //-----------Instances
    String INSTANCE_ACTION_NONE = "none";
    String INSTANCE_ACTION_SKIP = "skip";
    String INSTANCE_ACTION_RECONFIGURE = Client.InstanceOperation.RECONFIGURE;
    String INSTANCE_ACTION_REINSTALL = Client.InstanceOperation.REINSTALL;
    String INSTANCE_ACTION_DELETE_AND_DEPLOY = "deleteAndDeploy";


    String ELASTICBOX_SCHEMAS = "http://elasticbox.net/schemas";

    String AUTOMATIC_UPDATES_OFF = "off";
    String AUTOMATIC_UPDATES_MAJOR = "major";
    String AUTOMATIC_UPDATES_MINOR = "minor";
    String AUTOMATIC_UPDATES_PATCH = "patch";

    String AMAZON_PROVIDER_TYPE = "Amazon Web Services";
    String WINDOWS_CLAIM = "windows";
    String LINUX_CLAIM = "linux";
    String CLOUD_FOUNDATION_SERVICE = "CloudFormation Service";

    String BINDING_TYPE_VARIABLE = "Binding";
    String PRIVATE_VISIBILITY = "private";

}
