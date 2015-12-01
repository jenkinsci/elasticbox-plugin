package com.elasticbox.jenkins.model.box.order;

import com.elasticbox.jenkins.model.AbstractModel;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.provider.Provider;
import com.elasticbox.jenkins.model.workspace.Workspace;

/**
 * Created by serna on 11/27/15.
 */
public class DeployBoxOrder extends AbstractModel{

    private ScriptBox boxToDeploy;

    private Workspace workspace;

    private PolicyBox deployPolicy;

    private Provider provider;


    public DeployBoxOrder(String id) {
        super(id);
    }

    public ScriptBox getBoxToDeploy() {
        return boxToDeploy;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public PolicyBox getDeployPolicy() {
        return deployPolicy;
    }

    public Provider getProvider() {
        return provider;
    }
}
