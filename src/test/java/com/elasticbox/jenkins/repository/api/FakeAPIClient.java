package com.elasticbox.jenkins.repository.api;

import com.elasticbox.APIClient;
import com.elasticbox.jenkins.UnitTestingUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;

/**
 * Created by serna on 12/3/15.
 */
public class FakeAPIClient implements APIClient{

    @Override
    public JSONArray getAllBoxes(String workspaceId) throws IOException {
        final JSONArray objects = new JSONArray();
        objects.add(UnitTestingUtils.getFakeScriptBox());
        objects.add(UnitTestingUtils.getFakePolicyBox());
        objects.add(UnitTestingUtils.getFakeCloudFormationManagedBox());
        objects.add(UnitTestingUtils.getFakeCloudFormationTemplateBox());
        objects.add(UnitTestingUtils.getFakeEmptyApplicationBox());

        return objects;
    }

    @Override
    public JSONObject getBox(String boxId) throws IOException {
        return null;
    }
}
