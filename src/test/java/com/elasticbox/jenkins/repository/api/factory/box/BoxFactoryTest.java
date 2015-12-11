package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.UnitTestingUtils;
import com.elasticbox.jenkins.model.box.*;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.TemplateCloudFormationBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by serna on 11/29/15.
 * Tests the JSON to Objects parsing
 */
public class BoxFactoryTest {




    @Test
    public void testCreateScriptBox() throws ElasticBoxModelException {

        ScriptBoxFactory factory = new ScriptBoxFactory();
        final ScriptBox createdBox = factory.create(UnitTestingUtils.getFakeScriptBox());

        assertTrue("box id was not set", createdBox.getId().equals("f3ef667a-2d3b-4846-af75-7d7996505a92"));
        assertTrue("box name was not set", createdBox.getName().equals("PruebaS3"));
        assertTrue("box type was not set", createdBox.getType() == BoxType.SCRIPT);
        assertTrue("box requirements was not properly set", createdBox.getRequirements().length == 2);
        assertTrue("box requirements was not properly set", createdBox.getRequirements()[0].equals("req1"));
        assertTrue("box requirements was not properly set", createdBox.getRequirements()[1].equals("req2"));

    }

    @Test
    public void testCreatePolicyBox() throws ElasticBoxModelException {

        PolicyBoxFactory factory = new PolicyBoxFactory();
        final PolicyBox policyBox = factory.create(UnitTestingUtils.getFakePolicyBox());

        assertTrue("policyBox id was not set", policyBox.getId().equals("0308884a-d373-4e37-9e4f-70c1645cad0b"));
        assertTrue("policyBox name was not set", policyBox.getName().equals("default-large-us-east-1"));
        assertTrue("policyBox type was not set", policyBox.getType() == BoxType.POLICY);
        assertTrue("policyBox requirements was not properly set", policyBox.getClaims().length == 2);
        assertTrue("policyBox claims was not properly set", policyBox.getClaims()[0].equals("large"));
        assertTrue("policyBox claims was not properly set", policyBox.getClaims()[1].equals("linux"));

    }

    @Test
    public void testCreateTemplateCloudFormationBox() throws ElasticBoxModelException {

        TemplateCloudFormationBoxFactory factory = new TemplateCloudFormationBoxFactory();
        final TemplateCloudFormationBox templateCloudFormationBox = factory.create(UnitTestingUtils.getFakeCloudFormationTemplateBox());

        assertTrue("box id was not set", templateCloudFormationBox.getId().equals("3d87d385-8710-47c3-951e-7112d8db25f4"));
        assertTrue("box name was not set", templateCloudFormationBox.getName().equals("CF Template"));
        assertTrue("box type was not set", templateCloudFormationBox.getCloudFormationType() == CloudFormationBoxType.TEMPLATE);
    }

    @Test
    public void testCreateManagedCloudFormationBox() throws ElasticBoxModelException {

        ManagedCloudFormationBoxFactory factory = new ManagedCloudFormationBoxFactory();
        final ManagedCloudFormationBox managedCloudFormationBox = factory.create(UnitTestingUtils.getFakeCloudFormationManagedBox());

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
            GenericBoxFactory boxFactory = new GenericBoxFactory();
            final AbstractBox abstractBox = boxFactory.create(jsonBox);
            assertTrue("box type was not properly set", abstractBox.getType() == types[counter]);
            counter++;
        }

    }



}