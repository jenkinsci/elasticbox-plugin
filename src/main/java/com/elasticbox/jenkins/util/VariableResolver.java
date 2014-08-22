/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.util;

import com.elasticbox.jenkins.builders.IInstanceProvider;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Project;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.json.JSONObject;

/**
 *
 * @author Phong Nguyen Le
 */
public final class VariableResolver {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{[a-zA-Z0-9_\\-.]+\\}");

    private final List<IInstanceProvider> instanceProviders;
    private final hudson.util.VariableResolver<String> resolver;
    private final EnvVars envVars;
    private final AbstractBuild build;
    private final Map<String, String> variableValueLookup;
    

    public VariableResolver(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
        instanceProviders = new ArrayList<IInstanceProvider>();
        for (Object builder : ((Project) build.getProject()).getBuilders()) {
            if (builder instanceof IInstanceProvider) {
                instanceProviders.add((IInstanceProvider) builder);
            }
        }
        resolver = build.getBuildVariableResolver();
        envVars = build.getEnvironment(listener);
        this.build = build;    
        variableValueLookup = new HashMap<String, String>();
        variableValueLookup.put("SLAVE_HOST_NAME", build.getBuiltOn().toComputer().getHostName());
    }
    
    public String resolve(String value) {
        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            String varName = match.substring(2, match.length() - 1);
            String varValue = resolver.resolve(varName);
            if (varValue == null) {
                varValue = envVars.get(varName);
            }
            if (varValue == null) {
                varValue = variableValueLookup.get(varName);
            }
            if (varValue == null) {
                varValue = match;
            }
            
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(varValue));
        }        
        matcher.appendTail(resolved);                
        return resolved.toString();
    }
    
    public JSONObject resolve(JSONObject variable) {
        String value = resolve(variable.getString("value"));
        variable.put("value", value);
        if ("Binding".equals(variable.getString("type")) && value.startsWith("com.elasticbox.jenkins.builders.")) {
            for (IInstanceProvider instanceProvider : instanceProviders) {
                if (value.equals(instanceProvider.getId())) {
                    variable.put("value", instanceProvider.getInstanceId(build));
                }
            }                
        }

        if (variable.getString("scope").isEmpty()) {
            variable.remove("scope");
        }
            
        return variable;
    }
    
}
