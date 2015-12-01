package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class ManagedCloudFormationBoxFactory implements IBoxFactory<ManagedCloudFormationBox> {
    @Override
    public ManagedCloudFormationBox create(JSONObject jsonObject) throws ElasticBoxModelException {

        ManagedCloudFormationBox managedCloudFormationBox = new ManagedCloudFormationBox.ComplexBuilder()
                .withManagedCloudFormationType(jsonObject.getJSONObject("profile").getString("schema"))
                .withId(jsonObject.getString("id"))
                .withName(jsonObject.getString("name"))
                .build();

        return managedCloudFormationBox;
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
                    return cloudFormationBoxType == CloudFormationBoxType.MANAGED;
                }
            } catch (ElasticBoxModelException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
