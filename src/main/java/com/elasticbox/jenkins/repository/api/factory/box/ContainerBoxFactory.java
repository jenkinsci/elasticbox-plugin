package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.application.ApplicationBox;
import com.elasticbox.jenkins.model.box.docker.ContainerBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public class ContainerBoxFactory extends AbstractBoxFactory<ContainerBox> {
    @Override
    public ContainerBox create(JSONObject jsonObject) throws ElasticBoxModelException {
        ContainerBox box = new ContainerBox.ComplexBuilder()
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .build();

        return  box;
    }
    @Override
    public boolean canCreate(JSONObject jsonObject) {
        return super.canCreate(jsonObject, BoxType.DOCKER);
    }
}
