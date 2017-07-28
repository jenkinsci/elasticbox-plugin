package com.elasticbox.jenkins.model.repository.api;

import com.elasticbox.ApiClient;
import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.profile.PolicyProfileType;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import net.sf.json.JSONArray;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestBoxRepository {

    @Test
    public void testGetAllBoxesButPolicyAndApplication() throws IOException, RepositoryException {

        final ApiClient api = mock(ApiClient.class);

        String workspace = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<AbstractBox> noPolicyAndNoApplicationBoxes = new BoxRepositoryApiImpl(api).getNoPolicyAndNoApplicationBoxes(workspace);

        for (AbstractBox box : noPolicyAndNoApplicationBoxes) {
            assertTrue("Application boxes should not be retrieved", box.getType() != BoxType.APPLICATION);
            assertTrue("Application boxes should not be retrieved", box.getType() != BoxType.POLICY);
        }
    }

    @Test
    public void testGetAllBoxesButPolicy() throws IOException, RepositoryException {

        final ApiClient api = mock(ApiClient.class);

        String workspace = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<AbstractBox> noPolicyAndNoApplicationBoxes = new BoxRepositoryApiImpl(api).getNoPolicyBoxes(workspace);

        for (AbstractBox box : noPolicyAndNoApplicationBoxes) {
            assertTrue("policy boxes should not be retrieved", box.getType() != BoxType.POLICY);
        }
    }

    @Test
    public void testGetNoCloudFormationPolicyBoxes() throws IOException, RepositoryException {

        final ApiClient api = mock(ApiClient.class);

        String workspace = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<PolicyBox> noCloudFormationPolicyBoxes = new BoxRepositoryApiImpl(api).getNoCloudFormationPolicyBoxes(workspace);

        for (PolicyBox box : noCloudFormationPolicyBoxes) {
            assertTrue("Only CF policy boxes should be retrieved", (box.getProfileType() instanceof PolicyProfileType));
        }
    }

    @Test
    public void testGetCloudFormationPolicyBoxes() throws IOException, RepositoryException {

        final ApiClient api = mock(ApiClient.class);

        String workspace = null;
        when(api.getAllBoxes(workspace)).thenReturn(UnitTestingUtils.getFakeJSONArrayContainingOneFakeBoxForEachType());

        final List<PolicyBox> noCloudFormationPolicyBoxes = new BoxRepositoryApiImpl(api).getCloudFormationPolicyBoxes(workspace);

        for (PolicyBox box : noCloudFormationPolicyBoxes) {
            assertTrue("Only CF policy boxes should be retrieved", (box.getProfileType() instanceof PolicyProfileType));
        }
    }

}
