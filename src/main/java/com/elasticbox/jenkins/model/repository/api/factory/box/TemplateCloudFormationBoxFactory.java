package com.elasticbox.jenkins.model.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.TemplateCloudFormationBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.api.factory.JSONFactoryUtils;
import net.sf.json.JSONObject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 11/29/15.
 */
public class TemplateCloudFormationBoxFactory extends AbstractBoxFactory<TemplateCloudFormationBox> {

    private static final Logger logger = Logger.getLogger(TemplateCloudFormationBoxFactory.class.getName());

    @Override
    public TemplateCloudFormationBox create(JSONObject jsonObject) throws ElasticBoxModelException {

        TemplateCloudFormationBox templateCloudFormationBox = new TemplateCloudFormationBox.ComplexBuilder()
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .withRequirements(JSONFactoryUtils.toStringArray(jsonObject.getJSONArray("requirements")))
                .build();

        return templateCloudFormationBox;
    }

    @Override
    public boolean canCreate(JSONObject jsonObject) {

        if(super.canCreate(jsonObject, BoxType.CLOUDFORMATION)){
            final String type = jsonObject.getString("type");
            try {
                final CloudFormationBoxType cloudFormationBoxType = CloudFormationBoxType.getType(type);
                return cloudFormationBoxType == CloudFormationBoxType.TEMPLATE;
            } catch (ElasticBoxModelException e) {
                logger.log(Level.SEVERE, "There is no CloudFormation type for type: "+type);
                e.printStackTrace();
            }

        }

        return false;
    }
}
