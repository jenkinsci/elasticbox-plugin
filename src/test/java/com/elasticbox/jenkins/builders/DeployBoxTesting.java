package com.elasticbox.jenkins.builders;

import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.SlaveConfiguration;
import com.elasticbox.jenkins.UnitTestingUtils;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;

import hudson.remoting.Callable;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.internal.matchers.Any;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by serna on 1/18/16
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Jenkins.class)
public class DeployBoxTesting {

//    private static final String ID = "com.elasticbox.jenkins.builders.DeployBox-b3258655-f5da-4270-9778-eac01c15de2c";
//    private static final String CLOUD = "elasticbox-7bce5c7c-f8bf-4bc2-8612-9d2f40c4e000";
//
//    @Before
//    public void setUp() throws Exception {
//        String fakeCloud = "fakeCloud";
//        String fakeCloudDescription = "fakeCloudDescription";
//        String fakeEndpointURL = "fakeEndpointURL";
//        String fakeToken = "";
//        int maxInstances = 10;
//        List<SlaveConfiguration> empty = new ArrayList<>();
//
//
//
//
//        final ElasticBoxCloud elasticBoxCloud = Mockito.mock(ElasticBoxCloud.class);
//        when(elasticBoxCloud.getClient()).thenReturn(client);
//
//        final AbstractCIBase abstractCIBase = Mockito.mock(AbstractCIBase.class);
//
//        // Need to mock Jenkins to read the console notes.
//        Jenkins jenkins = mock(Jenkins.class);
//        PowerMockito.mockStatic(Jenkins.class);
//        when(Jenkins.getInstance()).thenReturn(jenkins);
//        when(jenkins.getCloud(any(String.class))).thenReturn(new ElasticBoxCloud(fakeCloud, fakeCloudDescription, fakeEndpointURL, maxInstances, fakeToken, empty));
//
//    }
//
//    @Test
//    public void testScriptBoxDeploy() throws IOException, InterruptedException {
//
//        String workspace = "operations";
//        String box = "5097988a-895c-4a7b-bf3a-2871a9144a20";
//        String boxVersion = "LATEST";
//        String instanceName = "RabbitMQ";
//        String profile = "1763dddd-2668-4959-8907-3c84b5e98b0e";
//        String claims = null;
//        String provider = null;
//        String location = null;
//        String instanceEnvVariable = "";
//        String tags = "";
//        String variables = "[{\"name\": \"username\", \"value\": \"username_value\", \"scope\": \"\", \"type\": \"Text\"}, {\"name\": \"password\", \"value\": \"password_value\", \"scope\": \"\", \"type\": \"Password\"}]";
//        InstanceExpiration expiration = new InstanceExpiration.AlwaysOn();
//        String autoUpdates = "off";
//        String alternateAction = "none";
//        boolean waitForCompletion = true;
//        int waitForCompletionTimeout=60;
//        String boxDeploymentType = "ScriptBox";
//
//        final Launcher launcher = Mockito.mock(Launcher.class);
//        final BuildListener buildListener = Mockito.mock(hudson.model.BuildListener.class);
//        when(buildListener.getLogger()).thenReturn(new PrintStream(System.out));
//
//        final Project project = Mockito.mock(Project.class);
//        when(project.getBuilders()).thenReturn(new ArrayList());
//
//        final Computer computer = Mockito.mock(Computer.class);
//        when(computer.getHostName()).thenReturn("fakeComputerName");
//
//        final Node node = PowerMockito.mock(Node.class);
//        when(node.toComputer()).thenReturn(computer);
//
//        final AbstractBuild build = Mockito.mock(AbstractBuild.class);
//        when(build.getProject()).thenReturn(project);
//        when(build.getBuildVariables()).thenReturn(new HashMap<String,String>());
//        when(build.getEnvironment(any(BuildListener.class))).thenReturn(new EnvVars());
//        when(build.getBuiltOn()).thenReturn(node);
//
//
//
//        final Jenkins instance = Jenkins.getInstance();
//
//        DeployBox deployBox = new DeployBox(ID,CLOUD,workspace,box,boxVersion,instanceName,profile,claims,provider,location,instanceEnvVariable,tags,variables,expiration,autoUpdates,alternateAction,waitForCompletion,waitForCompletionTimeout,boxDeploymentType);
//        deployBox.perform(build, launcher, buildListener);
//
//        final JSONObject fakeScriptBoxDeployRequest = UnitTestingUtils.getFakeScriptBoxDeployRequest();
//    }

}
