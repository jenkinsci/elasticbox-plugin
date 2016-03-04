/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes;

import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.member.Member;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract  class AbstractBoxTransformer<R extends AbstractBox> implements BoxTransformer<JSONObject, R> {

    private static final Logger logger = Logger.getLogger(AbstractBoxTransformer.class.getName());

    protected boolean canCreate(JSONObject jsonObject, BoxType typeToCheck) {
        final String schema = jsonObject.getString("schema");
        if (StringUtils.isNotBlank(schema) && BoxType.isBox(schema)) {
            try {
                final BoxType boxType  = BoxType.getType(schema);
                return boxType == typeToCheck;
            } catch (ElasticBoxModelException e) {
                logger.log(Level.SEVERE, "There is no BoxType for type: " + schema);
                e.printStackTrace();
            }
        }
        return false;
    }

    protected Member[] getMembers(JSONArray memberArrayObject) {
        if (!memberArrayObject.isEmpty()) {
            int counter = 0;
            Member [] members = new Member[memberArrayObject.size()];
            for (Object memberObject : memberArrayObject) {
                JSONObject memberJson = (JSONObject) memberObject;
                Member member = new Member(
                        Member.Role.findByValue(memberJson.getString("role")),
                        memberJson.getString("workspace"));

                members[counter] = member;
                counter++;
            }
            return members;
        }
        return new Member[0];
    }

}
