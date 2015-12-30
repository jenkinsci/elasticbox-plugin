package com.elasticbox.jenkins.model.box.docker;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang.StringUtils;

/**
 * Created by serna on 11/26/15.
 */
public class ContainerBox extends AbstractBox {

    public ContainerBox(String id, String name) {
        super(id, name, BoxType.DOCKER);
    }

    public static class ComplexBuilder {

        private String newId;
        private String newName;

        public ComplexBuilder() {}

        public NameBuilder withId(String id) {
            newId = id;
            return new NameBuilder();
        }

        public class NameBuilder {
            private NameBuilder() {}
            public BoxBuilder withName( String name ) {
                newName = name;
                return new BoxBuilder();
            }
        }

        public class BoxBuilder {
            private BoxBuilder() {}

            public ContainerBox build() throws ElasticBoxModelException {
                if (StringUtils.isNotEmpty(newId) &&
                        StringUtils.isNotEmpty(newName)){
                    return  new ContainerBox(newId, newName);
                }

                throw new ElasticBoxModelException("Not valid parameters for building Container(Docker) Box");
            }
        }
    }
}
