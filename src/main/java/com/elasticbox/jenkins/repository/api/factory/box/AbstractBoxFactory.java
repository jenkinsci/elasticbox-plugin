package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 12/3/15.
 */
public abstract  class AbstractBoxFactory<T extends AbstractBox> implements BoxFactory<T> {

    private static final Logger logger = Logger.getLogger(AbstractBoxFactory.class.getName());

    public boolean canCreate(JSONObject jsonObject, BoxType typeToCheck) {
        final String schema = jsonObject.getString("schema");
        if(StringUtils.isNotBlank(schema) && BoxType.isBox(schema)){
            try {
                final BoxType boxType  = BoxType.getType(schema);
                return boxType == typeToCheck;
            } catch (ElasticBoxModelException e) {
                logger.log(Level.SEVERE, "There is no BoxType for type: "+schema);
                e.printStackTrace();
            }
        }
        return false;
    }
}
