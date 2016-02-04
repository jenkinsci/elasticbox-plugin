package com.elasticbox.jenkins.model.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.application.ApplicationBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;

/**
 * Created by serna on 11/29/15.
 */
public class ApplicationBoxFactory extends AbstractBoxFactory<ApplicationBox> {
    @Override
    public ApplicationBox create(JSONObject jsonObject) throws ElasticBoxModelException {
        ApplicationBox box = new ApplicationBox.ApplicationBoxBuilder()
                .withOwner(jsonObject.getString("owner"))
                .withId(jsonObject.getString("id"))
                .withMembers(getMembers(jsonObject.getJSONArray("members")))
                .withName(jsonObject.getString("name"))
                .build();

        return  box;
    }
    @Override
    public boolean canCreate(JSONObject jsonObject) {
        return super.canCreate(jsonObject, BoxType.APPLICATION);
    }
}
