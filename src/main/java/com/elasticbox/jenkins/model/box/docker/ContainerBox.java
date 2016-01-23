package com.elasticbox.jenkins.model.box.docker;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang.StringUtils;

/**
 * Created by serna on 11/26/15.
 */
public class ContainerBox extends AbstractBox {

    public ContainerBox(ContainerBoxBuilder builder) {
        super(builder);
    }

    public static class ContainerBoxBuilder extends AbstractBox.ComplexBuilder<ContainerBoxBuilder, ContainerBox> {

        public ContainerBoxBuilder() {
            this.type = BoxType.DOCKER;
        }

        @Override
        public ContainerBox build() {
            return new ContainerBox(this);
        }
    }

}
