package com.elasticbox.jenkins.repository.api.factory;

import net.sf.json.JSONArray;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Created by serna on 11/30/15.
 */
public class JSONFactoryUtils {

    public static String [] toStringArray(JSONArray jsonArray){

        if(!jsonArray.isEmpty()){
            return (String[]) jsonArray.toArray(new String[jsonArray.size()]);
        }

        return new String[0];
    }

}
