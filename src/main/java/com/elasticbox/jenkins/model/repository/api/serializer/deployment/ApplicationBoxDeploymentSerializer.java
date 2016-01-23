/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.repository.api.serializer.deployment;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.services.deployment.execution.context.ApplicationBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.deployment.execution.order.ApplicationBoxDeploymentOrder;
import net.sf.json.JSONObject;

/**
 * Created by serna on 1/22/16.
 */
public class ApplicationBoxDeploymentSerializer implements BoxDeploymentRequestSerializer<ApplicationBoxDeploymentOrder, ApplicationBoxDeploymentContext> {

    @Override
    public JSONObject createRequest(ApplicationBoxDeploymentContext context) {

        final ApplicationBoxDeploymentOrder order = context.getOrder();
        final ApplicationBoxDeploymentRequestObject applicationBoxDeploymentRequestObject = new ApplicationBoxDeploymentRequestObject(
                order.getName(),
                order.getOwner(),
                Constants.BASE_ELASTICBOX_SCHEMA + Constants.DEPLOYMENT_APPLICATION_REQUEST_SCHEMA_NAME,
                order.getTags(),
                order.getRequirements(),
                new Box(context.getBoxToDeployId()),
                new Lease(order.getExpirationTime(), order.getExpirationOperation()));

        return JSONObject.fromObject(applicationBoxDeploymentRequestObject);
    }


    public static class ApplicationBoxDeploymentRequestObject{
        private String name;
        private String owner;
        private String schema;
        private String [] instance_tags;
        private String [] requirements;
        private Box box;
        private Lease lease;

        public ApplicationBoxDeploymentRequestObject(String name, String owner, String schema, String[] instance_tags, String[] requirements, Box box, Lease lease) {
            this.name = name;
            this.owner = owner;
            this.schema = schema;
            this.instance_tags = instance_tags;
            this.requirements = requirements;
            this.box = box;
            this.lease = lease;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String[] getInstance_tags() {
            return instance_tags;
        }

        public void setInstance_tags(String[] instance_tags) {
            this.instance_tags = instance_tags;
        }

        public String[] getRequirements() {
            return requirements;
        }

        public void setRequirements(String[] requirements) {
            this.requirements = requirements;
        }

        public Box getBox() {
            return box;
        }

        public void setBox(Box box) {
            this.box = box;
        }

        public Lease getLease() {
            return lease;
        }

        public void setLease(Lease lease) {
            this.lease = lease;
        }
    }

    public static class Box{
        private String id;

        public Box(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
    public static class Lease{
        private String expire;
        private String operation;

        public Lease(String expire, String operation) {
            this.expire = expire;
            this.operation = operation;
        }

        public String getExpire() {
            return expire;
        }

        public void setExpire(String expire) {
            this.expire = expire;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }
    }
}
