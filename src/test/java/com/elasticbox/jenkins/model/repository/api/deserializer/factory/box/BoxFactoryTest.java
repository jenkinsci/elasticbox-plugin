package com.elasticbox.jenkins.model.repository.api.deserializer.factory.box;

import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.box.*;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.TemplateCloudFormationBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.api.deserializer.Utils;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes.*;
import net.sf.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

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
    public void testCreateTemplateCloudFormationBox() throws ElasticBoxModelException {

        TemplateCloudFormationBoxTransformer transformer = new TemplateCloudFormationBoxTransformer();
        final TemplateCloudFormationBox templateCloudFormationBox = transformer.apply(UnitTestingUtils.getFakeCloudFormationTemplateBox());

        assertTrue("box id was not set", templateCloudFormationBox.getId().equals("3d87d385-8710-47c3-951e-7112d8db25f4"));
        assertTrue("box name was not set", templateCloudFormationBox.getName().equals("CF Template"));
        assertTrue("box type was not set", templateCloudFormationBox.getCloudFormationType() == CloudFormationBoxType.TEMPLATE);
    }

    @Test
    public void testCreateManagedCloudFormationBox() throws ElasticBoxModelException {

        ManagedCloudFormationBoxTransformer transformer = new ManagedCloudFormationBoxTransformer();
        final ManagedCloudFormationBox managedCloudFormationBox = transformer.apply(UnitTestingUtils.getFakeCloudFormationManagedBox());

        assertTrue("box member role was not set", managedCloudFormationBox.getMembers()[0].getRole().getValue().equals("collaborator"));
        assertTrue("box memmber workspace set", managedCloudFormationBox.getMembers()[0].getWorkspace().equals("jenkins1"));

        assertTrue("box id was not set", managedCloudFormationBox.getId().equals("02fab23c-5278-41ec-8d9e-0f7936582937"));
        assertTrue("box name was not set", managedCloudFormationBox.getName().equals("CF Managed"));
        assertTrue("box type was not set", managedCloudFormationBox.getCloudFormationType() == CloudFormationBoxType.MANAGED);
    }

    @Test
    public void testCreateAbstractBoxType() throws ElasticBoxModelException {

        BoxType [] types = new BoxType[]{BoxType.SCRIPT, BoxType.POLICY, BoxType.CLOUDFORMATION, BoxType.CLOUDFORMATION, BoxType.APPLICATION};
        JSONObject [] boxes = new JSONObject[]{
            UnitTestingUtils.getFakeScriptBox(),
                UnitTestingUtils.getFakePolicyBox(),
                    UnitTestingUtils.getFakeCloudFormationTemplateBox(),
                        UnitTestingUtils.getFakeCloudFormationManagedBox(),
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