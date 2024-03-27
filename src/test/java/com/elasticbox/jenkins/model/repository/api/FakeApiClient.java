package com.elasticbox.jenkins.model.repository.api;

import com.elasticbox.ApiClient;
import com.elasticbox.jenkins.UnitTestingUtils;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class FakeApiClient implements ApiClient {

    @Override
    public JSONArray getBoxVersions(String boxId) throws IOException {
        return null;
    }

    @Override
    public JSONArray getAllBoxes(String workspaceId) throws IOException {
        final JSONArray objects = new JSONArray();
        objects.add(UnitTestingUtils.getFakeScriptBox());
        objects.add(UnitTestingUtils.getFakePolicyBox());
        objects.add(UnitTestingUtils.getFakeCloudFormationTemplateBox());
        objects.add(UnitTestingUtils.getFakeEmptyApplicationBox());
        return objects;
    }

    @Override
    public JSONObject getBox(String boxId) throws IOException {
        //TODO Implement for testing
        return null;
    }

    @Override
    public JSONObject getInstance(String instanceId) throws IOException {
        //TODO Implement for testing
        return null;
    }

    @Override
    public JSONArray getInstances(String workspaceId, List<String> instanceIDs) throws IOException {
        //TODO Implement for testing
        return null;
    }

    @Override
    public <T extends JSON> T doPost(String url, JSONObject resource, boolean isArray) throws IOException {
        //TODO
        return null;
    }

    @Override
    public JSONArray getWorkspaces() throws IOException {
        //TODO
        return null;
    }

}
