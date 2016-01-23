/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.services.deployment.execution.context;

import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.builders.DeployBox;
import com.elasticbox.jenkins.model.services.deployment.DeploymentType;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;
import hudson.AbortException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.lang.StringUtils;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by serna on 1/23/16.
 */
public abstract class DeploymentContextFactory<T extends AbstractBoxDeploymentContext> {

    private static final Logger logger = Logger.getLogger(DeploymentContextFactory.class.getName());

    protected static EnumMap<DeploymentType, DeploymentContextFactory> deploymentTypeMap = new EnumMap<DeploymentType, DeploymentContextFactory>(DeploymentType.class);

    public abstract ApplicationBoxDeploymentContext createContext(DeployBox deployBox,
                                                                  VariableResolver variableResolver,
                                                                  ElasticBoxCloud cloud,
                                                                  AbstractBuild<?, ?> build,
                                                                  Launcher launcher,
                                                                  BuildListener listener,
                                                                  TaskLogger logger) throws AbortException;

    static{
        deploymentTypeMap.put(DeploymentType.APPLICATIONBOX_DEPLOYMENT_TYPE, new ApplicationBoxDeploymentContext.ApplicationBoxDeploymentFactory());
    }


    public static <T extends AbstractBoxDeploymentContext> T createDeploymentContext(DeployBox deployBox,
                                            VariableResolver variableResolver,
                                            ElasticBoxCloud cloud,
                                            AbstractBuild<?, ?> build,
                                            Launcher launcher,
                                            BuildListener listener,
                                            TaskLogger taskLogger) throws AbortException, ServiceException {

        final String boxDeploymentType = deployBox.getBoxDeploymentType();
        if(StringUtils.isNotBlank(boxDeploymentType)){
            final DeploymentType deploymentType = DeploymentType.findBy(boxDeploymentType);
            if (deploymentTypeMap.containsKey(deploymentType)){
                final DeploymentContextFactory deploymentContextFactory = deploymentTypeMap.get(deploymentType);
                return (T) deploymentContextFactory.createContext(deployBox, variableResolver, cloud, build, launcher, listener, taskLogger);
            }
        }

        taskLogger.error("There is no DeploymentContextFactory for DeploymentType: {0}", boxDeploymentType);
        logger.log(Level.SEVERE, "There is no DeploymentContextFactory for DeploymentType: " + boxDeploymentType);

        throw new ServiceException("There is no DeploymentContextFactory for DeploymentType: "+boxDeploymentType);

    }

    protected String [] commaSeparatedValuesToArray(String commaSeparated){
        if (StringUtils.isNotBlank(commaSeparated)) {
            Set<String> set = new HashSet<String>();
            for (String token : commaSeparated.split(",")) {
                set.add(token.trim());
            }
            return set.toArray(new String[set.size()]);
        }
        return null;
    }


}
