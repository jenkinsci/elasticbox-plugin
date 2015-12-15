package com.elasticbox.jenkins.repository.api;

import com.elasticbox.APIClient;
import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.model.profile.ProfileType;
import com.elasticbox.jenkins.repository.error.RepositoryException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.stubbing.OngoingStubbing;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Created by serna on 12/3/15.
 */
public class TestBoxRepository {

    @Test
    public void testGetAllBoxesButPolicyAndApplication() throws IOException, RepositoryException {

        final APIClient api = mock(APIClient.class);

        String workspace = null;
        JSONArray array = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<AbstractBox> noPolicyAndNoApplicationBoxes = new BoxRepositoryAPIImpl(api).getNoPolicyAndNoApplicationBoxes(workspace);

        for (AbstractBox box : noPolicyAndNoApplicationBoxes) {
            assertTrue("Application boxes should not be retrieved", box.getType() != BoxType.APPLICATION && box.getType() != BoxType.POLICY);
        }
    }

    @Test
    public void testGetAllBoxesButPolicy() throws IOException, RepositoryException {

        final APIClient api = mock(APIClient.class);

        String workspace = null;
        JSONArray array = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<AbstractBox> noPolicyAndNoApplicationBoxes = new BoxRepositoryAPIImpl(api).getNoPolicyBoxes(workspace);

        for (AbstractBox box : noPolicyAndNoApplicationBoxes) {
            assertTrue("policy boxes should not be retrieved", box.getType() != BoxType.POLICY);
        }
    }

    @Test
    public void testGetNoCloudFormationPolicyBoxes() throws IOException, RepositoryException {

        final APIClient api = mock(APIClient.class);

        String workspace = null;
        JSONArray array = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<PolicyBox> noCloudFormationPolicyBoxes = new BoxRepositoryAPIImpl(api).getNoCloudFormationPolicyBoxes(workspace);

        for (PolicyBox box : noCloudFormationPolicyBoxes) {
            assertTrue("Only CF policy boxes should be retrieved", (box.getProfileType() instanceof PolicyProfileType));
        }
    }

    @Test
    public void testGetCloudFormationPolicyBoxes() throws IOException, RepositoryException {

        final APIClient api = mock(APIClient.class);

        String workspace = null;
        JSONArray array = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<PolicyBox> noCloudFormationPolicyBoxes = new BoxRepositoryAPIImpl(api).getCloudFormationPolicyBoxes(workspace);

        for (PolicyBox box : noCloudFormationPolicyBoxes) {
            assertTrue("Only CF policy boxes should be retrieved", (box.getProfileType() instanceof PolicyProfileType));
        }
    }

}
