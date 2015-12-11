package com.elasticbox.jenkins.model.box;

import com.elasticbox.jenkins.model.error.ElasticBoxModelException;

/**
 * Created by serna on 11/26/15.
 */
public enum BoxType{

    SCRIPT("/boxes/script"),
    POLICY("/boxes/policy"),
    APPLICATION("/boxes/application"),
    CLOUDFORMATION("/boxes/cloudformation"),
    DOCKER("/boxes/docker")
    ;

    private final String schema;

    BoxType(String schema) {
        this.schema = schema;
    }

    public static boolean isBox(String schema){
        BoxType[] values = BoxType.values();
        for (BoxType value : values) {
                if(value.isType(schema))
                    return true;
        }
        return false;
    }

    public static BoxType getType(String schema) throws ElasticBoxModelException {
        BoxType[] values = BoxType.values();
        for (BoxType boxType : values) {
            if(boxType.isType(schema))
                return boxType;
        }
        throw new ElasticBoxModelException("There is no box type whose schema ends with: "+schema);
    }

    public boolean isType(String schema){
        return schema.endsWith(this.schema);
    }

    public String getSchema() {
        return schema;
    }

}
