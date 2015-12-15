package com.elasticbox;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;

/**
 * Created by serna on 11/27/15.
 */
public interface APIClient {

    public JSONArray getAllBoxes(String workspaceId) throws IOException;

    public JSONObject getBox(String boxId) throws IOException;
}
