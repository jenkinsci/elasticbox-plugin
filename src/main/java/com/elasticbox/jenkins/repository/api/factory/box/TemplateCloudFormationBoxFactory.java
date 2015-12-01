package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.TemplateCloudFormationBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.repository.api.factory.JSONFactoryUtils;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class TemplateCloudFormationBoxFactory implements IBoxFactory<TemplateCloudFormationBox> {
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
        final String schema = jsonObject.getString("schema");
        if(StringUtils.isNotBlank(schema) && BoxType.isBox(schema)){
            try {
                final BoxType boxType  = BoxType.geType(schema);
                if(boxType == BoxType.CLOUDFORMATION){
                    final String type = jsonObject.getString("type");
                    final CloudFormationBoxType cloudFormationBoxType = CloudFormationBoxType.geType(type);
                    return cloudFormationBoxType == CloudFormationBoxType.TEMPLATE;
                }
            } catch (ElasticBoxModelException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
