package com.elasticbox.jenkins.model.instance;

import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.AbstractModel;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.application.ApplicationBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.variable.Variable;
import org.apache.commons.lang.StringUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/27/15.
 */
public class Instance extends AbstractModel {

    public Instance(String id, String name, Operation operation, String schema, Service service, State state,
                    String deleted, AutomaticUpdates automaticUpdates, String box, AbstractBox [] boxes,
                    String [] tags, String uri, PolicyBox policyBox, String owner) {
        super(id);
        this.name = name;
        this.operation = operation;
        this.schema = schema;
        this.service = service;
        this.state = state;
        this.deleted = deleted;
        this.automaticUpdates = automaticUpdates;
        this.box = box;
        this.boxes = boxes;
        this.tags = tags;
        this.uri = uri;
        this.owner = owner;
        this.policyBox = policyBox;
    }

    public enum State {
        PROCESSING("processing"), DONE("done"), UNAVAILABLE("unavailable");
        private String value;
        State(String value){
            this.value = value;
        }

        public String getValue() {
            return value;
        }
        public static State findByValue(String state) throws ElasticBoxModelException {
            State[] values = State.values();
            for (State stateValue : values) {
                if (stateValue.getValue().equals(state)){
                    return stateValue;
                }
            }
            throw new ElasticBoxModelException("There is no Instance state for value: "+state);
        }

    }

    public enum AutomaticUpdates {
        MAJOR("major"), MINOR("minor"), PATCH("patch"), OFF("off");
        private String value;
        AutomaticUpdates(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
        public static AutomaticUpdates findByValue(String automaticUpdates) throws ElasticBoxModelException {
            AutomaticUpdates [] values = AutomaticUpdates.values();
            for (AutomaticUpdates automaticUpdatesValue : values) {
                if (automaticUpdatesValue.getValue().equals(automaticUpdates)){
                    return automaticUpdatesValue;
                }
            }
            throw new ElasticBoxModelException("There is no Instance automatic updates for value: "+automaticUpdates);
        }
    }

    public enum OperationType {
        DEPLOY("deploy"),
        SHUTDOWN("shutdown"),
        SHUTDOWN_SERVICE("shutdown_service"),
        POWER_ON("poweron"),
        REINSTALL("reinstall"),
        RECONFIGURE("reconfigure"),
        TERMINATE("terminate"),
        TERMINATE_SERVICE("terminate_service"),
        SNAPSHOT("snapshot");

        private String value;
        OperationType(String value){
            this.value = value;
        }

        public String getValue() {
            return value;
        }
        public static OperationType findByValue(String operationType) throws ElasticBoxModelException {
            OperationType [] values = OperationType.values();
            for (OperationType operationTypeValue : values) {
                if (operationTypeValue.getValue().equals(operationType)){
                    return operationTypeValue;
                }
            }
            throw new ElasticBoxModelException("There is no Instance operation type for value: "+operationType);
        }
    }

    private String deleted;
    private String box;
    private String name;
    private String schema;
    private String uri;
    private String owner;

    private State state;
    private AutomaticUpdates automaticUpdates;
    private ApplicationBox applicationBox;
    private PolicyBox policyBox;
    private String [] tags;
    private Variable [] variables;
    private AbstractBox [] boxes;
    private Operation operation;
    private Service service;

    public String getInstancePageURL(String endpointUrl){
        return MessageFormat.format(Constants.INSTANCES_PAGE_URL_PATTERN, endpointUrl, getId(), getDasherizedName());
    }

    public String getDasherizedName(){
            return getName().replaceAll("[^a-z0-9-]", "-").toLowerCase();
    }

    public Service getService() {
        return service;
    }

    public Operation getOperation() {
        return operation;
    }

    public State getState() {
        return state;
    }

    public String getDeleted() {
        return deleted;
    }

    public String getBox() {
        return box;
    }

    public String getSchema() {
        return schema;
    }

    public String getOwner() {
        return owner;
    }

    public AutomaticUpdates getAutomaticUpdates() {
        return automaticUpdates;
    }

    public ApplicationBox getApplicationBox() {
        return applicationBox;
    }

    public PolicyBox getPolicyBox() {
        return policyBox;
    }

    public Variable[] getVariables() {
        return variables;
    }

    public AbstractBox[] getBoxes() {
        return boxes;
    }

    public String [] getTags() {
        return tags;
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public static class Service{
        private String id;

        public Service(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class Operation{
        private String workspace;
        private String created;
        private OperationType operationType;

        public Operation(OperationType type, String created, String workspace) {
            this.created = created;
            this.workspace = workspace;
            this.operationType = type;
        }

        public String getWorkspace() {
            return workspace;
        }

        public String getCreated() {
            return created;
        }

        public OperationType getOperationType() {
            return operationType;
        }
    }

    public static class ComplexBuilder {

        private State state;

        private String id;
        private String uri;
        private String deleted;
        private String box;
        private String name;
        private String schema;
        private String owner;

        private Service service;
        private Operation operation;
        private AutomaticUpdates automaticUpdates;
        private ApplicationBox applicationBox;
        private PolicyBox policyBox;
        private String [] tags;
        private Variable [] variables;
        private AbstractBox [] boxes;

        public ComplexBuilder() {}

        public BoxBuilder withSchema(String newSchema) {
            schema = newSchema;
            return new BoxBuilder();
        }

        public class BoxBuilder{
            private BoxBuilder() {}
            public AutomaticUpdatesBuilder withBox(String newBox) {
                box = newBox;
                return new AutomaticUpdatesBuilder();
            }
        }

        public class AutomaticUpdatesBuilder {
            private AutomaticUpdatesBuilder() {}
            public BoxesBuilder withAutomaticUpdates(AutomaticUpdates newAutomaticUpdates) {
                automaticUpdates = newAutomaticUpdates;
                return new BoxesBuilder();
            }
        }

        public class BoxesBuilder {
            private BoxesBuilder() {}
            public NameBuilder withBoxes(AbstractBox [] newBoxes) {
                boxes = newBoxes;
                return new NameBuilder();
            }
        }

        public class NameBuilder {
            private NameBuilder() {}
            public OperationBuilder withName(String newName) {
                 name = newName;
                return new OperationBuilder();
            }
        }

        public class OperationBuilder {
            private OperationBuilder() {}
            public ServiceBuilder withOperation(Operation newOperation) {
                operation = newOperation;
                return new ServiceBuilder();
            }
        }

        public class ServiceBuilder {
            private ServiceBuilder() {}
            public StateBuilder withService(Service newService) {
                service = newService;
                return new StateBuilder();
            }
        }

        public class StateBuilder {
            private StateBuilder() {}
            public TagsBuilder withState(State newState) {
                state = newState;
                return new TagsBuilder();
            }
        }

        public class TagsBuilder {
            private TagsBuilder() {}
            public IdBuilder withTags(String [] newTags) {
                tags = newTags;
                return new IdBuilder();
            }
        }

        public class IdBuilder {
            private IdBuilder() {}
            public URIBuilder withId(String newId) {
                id = newId;
                return new URIBuilder();
            }
        }

        public class URIBuilder {
            private URIBuilder() {}
            public OwnerBuilder withURI(String newUri) {
                uri = newUri;
                return new OwnerBuilder();
            }
        }

        public class OwnerBuilder {
            private OwnerBuilder() {}
            public InstanceBuilder withOwner(String newOwner) {
                owner = newOwner;
                return new InstanceBuilder();
            }
        }

        public class InstanceBuilder {
            private InstanceBuilder() {}

            public InstanceBuilder withApplicationBox(ApplicationBox newApplicationBox){
                applicationBox = newApplicationBox;
                return this;
            }

            public InstanceBuilder withPolicyBox(PolicyBox newPolicyBox){
                policyBox = newPolicyBox;
                return this;
            }

            public Instance build() throws ElasticBoxModelException {
                return new Instance(id, name, operation, schema, service, state, deleted, automaticUpdates, box,
                        boxes,tags, uri, policyBox, owner);
            }
        }

    }
}
