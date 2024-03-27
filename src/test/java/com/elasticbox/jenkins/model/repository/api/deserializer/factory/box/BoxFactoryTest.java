package com.elasticbox.jenkins.model.repository.api.deserializer.factory.box;

import static org.junit.Assert.assertTrue;

import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.BoxType;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes.BoxFactory;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes.PolicyBoxTransformer;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes.ScriptBoxTransformer;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes.CloudFormationBoxTransformer;
import net.sf.json.JSONObject;
import org.junit.Test;

public class BoxFactoryTest {

    @Test
    public void testCreateScriptBox() throws ElasticBoxModelException {

        final JSONObject fakeScriptBox = UnitTestingUtils.getFakeScriptBox();
        ScriptBoxTransformer transformer = new ScriptBoxTransformer();
        final ScriptBox scriptBox = transformer.apply(fakeScriptBox);

        assertTrue("box id was not set", scriptBox.getId().equals("f3ef667a-2d3b-4846-af75-7d7996505a92"));
        assertTrue("box name was not set", scriptBox.getName().equals("PruebaS3"));
        assertTrue("box type was not set", scriptBox.getType() == BoxType.SCRIPT);
        assertTrue("box requirements was not properly set", scriptBox.getRequirements().length == 2);
        assertTrue("box requirements was not properly set", scriptBox.getRequirements()[0].equals("req1"));
        assertTrue("box requirements was not properly set", scriptBox.getRequirements()[1].equals("req2"));
    }

    @Test
    public void testCreatePolicyBox() throws ElasticBoxModelException {

        PolicyBoxTransformer transformer = new PolicyBoxTransformer();
        final PolicyBox policyBox = transformer.apply(UnitTestingUtils.getFakePolicyBox());

        assertTrue("policyBox id was not set", policyBox.getId().equals("0308884a-d373-4e37-9e4f-70c1645cad0b"));
        assertTrue("policyBox name was not set", policyBox.getName().equals("default-large-us-east-1"));
        assertTrue("policyBox type was not set", policyBox.getType() == BoxType.POLICY);
        assertTrue("policyBox requirements was not properly set", policyBox.getClaims().length == 2);
        assertTrue("policyBox claims was not properly set", policyBox.getClaims()[0].equals("large"));
        assertTrue("policyBox claims was not properly set", policyBox.getClaims()[1].equals("linux"));
    }

    @Test
    public void testCreateCloudFormationBox() throws ElasticBoxModelException {

        CloudFormationBoxTransformer transformer = new CloudFormationBoxTransformer();
        final CloudFormationBox cloudFormationBox = transformer.apply(UnitTestingUtils.getFakeCloudFormationTemplateBox());

        assertTrue("box id was not set", cloudFormationBox.getId().equals("3d87d385-8710-47c3-951e-7112d8db25f4"));
        assertTrue("box name was not set", cloudFormationBox.getName().equals("CF Template"));
        assertTrue("box type was not set", cloudFormationBox.getCloudFormationType() == "CloudFormation Service");
    }

    @Test
    public void testCreateAbstractBoxType() throws ElasticBoxModelException {

        BoxType [] types = new BoxType[]{BoxType.SCRIPT, BoxType.POLICY, BoxType.CLOUDFORMATION, BoxType.APPLICATION};
        JSONObject [] boxes = new JSONObject[]{
            UnitTestingUtils.getFakeScriptBox(),
            UnitTestingUtils.getFakePolicyBox(),
            UnitTestingUtils.getFakeCloudFormationTemplateBox(),
            UnitTestingUtils.getFakeEmptyApplicationBox()
        };

        int counter = 0;
        for (JSONObject jsonBox : boxes) {
            final AbstractBox abstractBox = new BoxFactory().apply(jsonBox);
            assertTrue("box type was not properly set", abstractBox.getType() == types[counter]);
            counter++;
        }
    }
}