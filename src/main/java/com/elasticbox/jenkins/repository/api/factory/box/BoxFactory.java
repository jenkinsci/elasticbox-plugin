package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.repository.api.factory.Factory;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class BoxFactory implements Factory<AbstractBox>{

    public static IBoxFactory [] boxFactories = new IBoxFactory[]{
        new ScriptBoxFactory(),
            new PolicyBoxFactory(),
                new TemplateCloudFormationBoxFactory(),
                    new ManagedCloudFormationBoxFactory()

    };

    public AbstractBox create(JSONObject jsonObject) throws ElasticBoxModelException {

        final String schema = jsonObject.getString("schema");

        if(StringUtils.isNotBlank(schema) && BoxType.isBox(schema)){

            final BoxType boxType = BoxType.geType(schema);

            for (IBoxFactory<AbstractBox> boxFactory : boxFactories) {
                if (boxFactory.canCreate(jsonObject))
                    return boxFactory.create(jsonObject);
            }

        }
        throw new ElasticBoxModelException("There is no factory for building: "+schema);
    }

}