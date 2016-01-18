package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 * Created by serna on 11/29/15.
 */
public class GenericBoxFactory extends AbstractBoxFactory<AbstractBox> {

    public static BoxFactory[] boxFactories = new BoxFactory[]{
        new ScriptBoxFactory(),
            new PolicyBoxFactory(),
                new TemplateCloudFormationBoxFactory(),
                    new ManagedCloudFormationBoxFactory(),
                        new ApplicationBoxFactory(),
                            new ContainerBoxFactory()

    };

    public AbstractBox create(JSONObject jsonObject) throws ElasticBoxModelException {

        final String schema = jsonObject.getString("schema");

        if(StringUtils.isNotBlank(schema) && BoxType.isBox(schema)){

            final BoxType boxType = BoxType.getType(schema);

            for (BoxFactory<AbstractBox> boxFactory : boxFactories) {
                if (boxFactory.canCreate(jsonObject))
                    return boxFactory.create(jsonObject);
            }

        }
        throw new ElasticBoxModelException("There is no factory for building: "+schema);
    }

    @Override
    public boolean canCreate(JSONObject jsonObject) {
        final String schema = jsonObject.getString("schema");
        return BoxType.isBox(schema);
    }
}