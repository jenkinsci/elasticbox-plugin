package com.elasticbox.jenkins.builders;

import com.elasticbox.jenkins.UnitTestingUtils;
import net.sf.json.JSONObject;
import org.junit.Test;

/**
 * Created by serna on 1/18/16.
 */
public class DeployBoxTesting {

    @Test
    public void testScriptBoxDeploy(){

        String id = "";
        String cloud = "";
        String workspace = "";
        String box = "";
        String boxVersion = "";
        String instanceName = "";
        String profile = "";
        String claims = "";
        String provider = "";
        String location = "";
        String instanceEnvVariable = "";
        String tags = "";
        String variables = "";
        InstanceExpiration expiration =null;
        String autoUpdates = "";
        String alternateAction = "";
        boolean waitForCompletion = true;
        int waitForCompletionTimeout=0;
        String boxDeploymentType = "";

        DeployBox deployBox = new DeployBox(id,cloud,workspace,box,boxVersion,instanceName,profile,claims,provider,location,instanceEnvVariable,tags,variables,expiration,autoUpdates,alternateAction,waitForCompletion,waitForCompletionTimeout,boxDeploymentType);

        final JSONObject fakeScriptBoxDeployRequest = UnitTestingUtils.getFakeScriptBoxDeployRequest();
    }

}
