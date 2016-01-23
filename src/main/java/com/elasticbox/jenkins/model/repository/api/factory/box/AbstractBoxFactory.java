package com.elasticbox.jenkins.model.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.member.Member;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

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

    protected Member[] getMembers(JSONArray memberArrayObject){
        if(!memberArrayObject.isEmpty()){
            int counter = 0;
            Member [] members = new Member[memberArrayObject.size()];
            for (Object memberObject : memberArrayObject) {
                JSONObject memberJson = (JSONObject) memberObject;
                Member member = new Member(Member.Role.findByValue(memberJson.getString("role")),memberJson.getString("workspace"));
                members[counter] = member;
                counter++;
            }
            return members;
        }
        return new Member[0];
    }

}
