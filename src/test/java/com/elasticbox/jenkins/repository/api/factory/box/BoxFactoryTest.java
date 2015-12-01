package com.elasticbox.jenkins.repository.api.factory.box;

import com.elasticbox.jenkins.model.box.*;
import com.elasticbox.jenkins.model.box.cloudformation.CloudFormationBoxType;
import com.elasticbox.jenkins.model.box.cloudformation.ManagedCloudFormationBox;
import com.elasticbox.jenkins.model.box.cloudformation.TemplateCloudFormationBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by serna on 11/29/15.
 */
public class BoxFactoryTest {

    private String  box = "{\n" +
            "\"updated\": \"2015-11-17 16:47:06.005841\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"requirements\": [\n" +
            "\"req1\",\n" +
            "\"req2\"\n" +
            "],\n" +
            "\"description\": \"desc of the box\",\n" +
            "\"name\": \"PruebaS3\",\n" +
            "\"created\": \"2015-11-05 23:47:02.643547\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [],\n" +
            "\"uri\": \"/services/boxes/f3ef667a-2d3b-4846-af75-7d7996505a92\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"id\": \"f3ef667a-2d3b-4846-af75-7d7996505a92\",\n" +
            "\"members\": [],\n" +
            "\"owner\": \"operations\",\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"events\": {},\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/script\"\n" +
            "}";

    String  policyBox = "{\n" +
            "\"profile\": {\n" +
            "\"subnet\": \"us-east-1a\",\n" +
            "\"cloud\": \"EC2\",\n" +
            "\"image\": \"Linux Compute\",\n" +
            "\"instances\": 1,\n" +
            "\"keypair\": \"None\",\n" +
            "\"location\": \"us-east-1\",\n" +
            "\"volumes\": [],\n" +
            "\"flavor\": \"m1.large\",\n" +
            "\"security_groups\": [\n" +
            "\"Automatic\"\n" +
            "],\n" +
            "\"schema\": \"http://elasticbox.net/schemas/aws/ec2/profile\"\n" +
            "},\n" +
            "\"provider_id\": \"77bb43a7-7122-44ba-aa6f-6f0886eccabd\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"name\": \"default-large-us-east-1\",\n" +
            "\"created\": \"2015-11-05 17:53:55.266635\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [],\n" +
            "\"updated\": \"2015-11-17 16:47:06.000420\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"uri\": \"/services/boxes/0308884a-d373-4e37-9e4f-70c1645cad0b\",\n" +
            "\"owner\": \"operations\",\n" +
            "\"members\": [],\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"readme\": {\n" +
            "\"url\": \"/resources/default_box_overview.md\",\n" +
            "\"upload_date\": \"2015-11-05 17:53:55.265901\",\n" +
            "\"length\": 1302,\n" +
            "\"content_type\": \"text/x-markdown\"\n" +
            "},\n" +
            "\"claims\": [\n" +
            "\"large\",\n" +
            "\"linux\"\n" +
            "],\n" +
            "\"id\": \"0308884a-d373-4e37-9e4f-70c1645cad0b\",\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/policy\"\n" +
            "}";

    private String templateCloudFormationBox = "{\n" +
            "\"updated\": \"2015-11-26 10:11:54.669276\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"requirements\": [],\n" +
            "\"name\": \"CF Template\",\n" +
            "\"created\": \"2015-11-25 16:40:12.054144\",\n" +
            "\"deleted\": null,\n" +
            "\"type\": \"CloudFormation Service\",\n" +
            "\"variables\": [\n" +
            "{\n" +
            "\"required\": false,\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"KeyName\",\n" +
            "\"value\": \"\",\n" +
            "\"visibility\": \"public\"\n" +
            "},\n" +
            "{\n" +
            "\"required\": false,\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"InstanceType\",\n" +
            "\"value\": \"m1.small\",\n" +
            "\"visibility\": \"public\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBName\",\n" +
            "\"value\": \"wordpressdb\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"SSHLocation\",\n" +
            "\"value\": \"0.0.0.0/0\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBPassword\",\n" +
            "\"value\": \"\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBUser\",\n" +
            "\"value\": \"\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBRootPassword\",\n" +
            "\"value\": \"\"\n" +
            "}\n" +
            "],\n" +
            "\"description\": \"Tiene policy\",\n" +
            "\"uri\": \"/services/boxes/3d87d385-8710-47c3-951e-7112d8db25f4\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"members\": [],\n" +
            "\"owner\": \"operations\",\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"template\": {\n" +
            "\"url\": \"/services/blobs/download/5656daea14841238d2f083a1/template.json\",\n" +
            "\"upload_date\": \"2015-11-26 10:11:54.628201\",\n" +
            "\"length\": 15489,\n" +
            "\"content_type\": \"text/x-shellscript\"\n" +
            "},\n" +
            "\"id\": \"3d87d385-8710-47c3-951e-7112d8db25f4\",\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/cloudformation\"\n" +
            "}";

    private String managedCloudFormationBox = "{\n" +
            "\"profile\": {\n" +
            "\"range\": {\n" +
            "\"type\": \"none\",\n" +
            "\"name\": \"\"\n" +
            "},\n" +
            "\"capacity\": {\n" +
            "\"read\": 5,\n" +
            "\"write\": 5\n" +
            "},\n" +
            "\"location\": \"ap-northeast-1\",\n" +
            "\"key\": {\n" +
            "\"type\": \"str\",\n" +
            "\"name\": \"Key Name\"\n" +
            "},\n" +
            "\"schema\": \"http://elasticbox.net/schemas/aws/ddb/profile\"\n" +
            "},\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/cloudformation\",\n" +
            "\"provider_id\": \"77bb43a7-7122-44ba-aa6f-6f0886eccabd\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"requirements\": [],\n" +
            "\"name\": \"CF Managed\",\n" +
            "\"created\": \"2015-11-25 16:39:14.925122\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Options\",\n" +
            "\"name\": \"key_type\",\n" +
            "\"value\": \"str\",\n" +
            "\"options\": \"int,long,float,str,unicode,Binary\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Text\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"str\",\n" +
            "\"name\": \"key_name\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Port\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"80\",\n" +
            "\"name\": \"port\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Text\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"\",\n" +
            "\"name\": \"range_name\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Options\",\n" +
            "\"name\": \"range_type\",\n" +
            "\"value\": \"none\",\n" +
            "\"options\": \"none,int,long,float,str,unicode,Binary\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Number\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"5\",\n" +
            "\"name\": \"read_capacity_units\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Number\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"5\",\n" +
            "\"name\": \"write_capacity_units\"\n" +
            "}\n" +
            "],\n" +
            "\"updated\": \"2015-11-25 16:40:25.599207\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"uri\": \"/services/boxes/02fab23c-5278-41ec-8d9e-0f7936582937\",\n" +
            "\"members\": [],\n" +
            "\"owner\": \"operations\",\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"type\": \"Dynamo DB Domain\",\n" +
            "\"id\": \"02fab23c-5278-41ec-8d9e-0f7936582937\",\n" +
            "\"description\": \"No tiene policy\"\n" +
            "}";

    @Test
    public void testCreateScriptBox() throws ElasticBoxModelException {


        JSONObject jsonBox = (JSONObject) JSONSerializer.toJSON(box);

        ScriptBoxFactory factory = new ScriptBoxFactory();
        final ScriptBox createdBox = factory.create(jsonBox);

        assertTrue("box id was not set", createdBox.getId().equals("f3ef667a-2d3b-4846-af75-7d7996505a92"));
        assertTrue("box name was not set", createdBox.getName().equals("PruebaS3"));
        assertTrue("box type was not set", createdBox.getType() == BoxType.SCRIPT);
        assertTrue("box requirements was not properly set", createdBox.getRequirements().length == 2);
        assertTrue("box requirements was not properly set", createdBox.getRequirements()[0].equals("req1"));
        assertTrue("box requirements was not properly set", createdBox.getRequirements()[1].equals("req2"));

    }

    @Test
    public void testCreatePolicyBox() throws ElasticBoxModelException {


        JSONObject jsonBox = (JSONObject) JSONSerializer.toJSON(policyBox);

        PolicyBoxFactory factory = new PolicyBoxFactory();
        final PolicyBox policyBox = factory.create(jsonBox);

        assertTrue("policyBox id was not set", policyBox.getId().equals("0308884a-d373-4e37-9e4f-70c1645cad0b"));
        assertTrue("policyBox name was not set", policyBox.getName().equals("default-large-us-east-1"));
        assertTrue("policyBox type was not set", policyBox.getType() == BoxType.POLICY);
        assertTrue("policyBox requirements was not properly set", policyBox.getClaims().length == 2);
        assertTrue("policyBox claims was not properly set", policyBox.getClaims()[0].equals("large"));
        assertTrue("policyBox claims was not properly set", policyBox.getClaims()[1].equals("linux"));

    }

    @Test
    public void testCreateTemplateCloudFormationBox() throws ElasticBoxModelException {


        JSONObject jsonBox = (JSONObject) JSONSerializer.toJSON(templateCloudFormationBox);

        TemplateCloudFormationBoxFactory factory = new TemplateCloudFormationBoxFactory();
        final TemplateCloudFormationBox templateCloudFormationBox = factory.create(jsonBox);

        assertTrue("box id was not set", templateCloudFormationBox.getId().equals("3d87d385-8710-47c3-951e-7112d8db25f4"));
        assertTrue("box name was not set", templateCloudFormationBox.getName().equals("CF Template"));
        assertTrue("box type was not set", templateCloudFormationBox.getCloudFormationType() == CloudFormationBoxType.TEMPLATE);
    }

    @Test
    public void testCreateManagedCloudFormationBox() throws ElasticBoxModelException {

        JSONObject jsonBox = (JSONObject) JSONSerializer.toJSON(this.managedCloudFormationBox);

        ManagedCloudFormationBoxFactory factory = new ManagedCloudFormationBoxFactory();
        final ManagedCloudFormationBox managedCloudFormationBox = factory.create(jsonBox);

        assertTrue("box id was not set", managedCloudFormationBox.getId().equals("02fab23c-5278-41ec-8d9e-0f7936582937"));
        assertTrue("box name was not set", managedCloudFormationBox.getName().equals("CF Managed"));
        assertTrue("box type was not set", managedCloudFormationBox.getCloudFormationType() == CloudFormationBoxType.MANAGED);
    }

    @Test
    public void testCreateAbstractBoxType() throws ElasticBoxModelException {

        BoxType [] types = new BoxType[]{BoxType.SCRIPT, BoxType.POLICY, BoxType.CLOUDFORMATION, BoxType.CLOUDFORMATION};
        String [] boxes = new String[]{box, policyBox, templateCloudFormationBox, managedCloudFormationBox};

        int counter = 0;
        for (String box : boxes) {
            JSONObject jsonBox = (JSONObject) JSONSerializer.toJSON(box);
            BoxFactory boxFactory = new BoxFactory();
            final AbstractBox abstractBox = boxFactory.create(jsonBox);
            assertTrue("box type was not properly set", abstractBox.getType() == types[counter]);
            counter++;
        }

    }



}