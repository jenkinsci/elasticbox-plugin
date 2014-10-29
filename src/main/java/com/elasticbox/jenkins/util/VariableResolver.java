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

import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.builders.IInstanceProvider;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.TaskListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public final class VariableResolver {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{[a-zA-Z0-9_\\-.]+\\}");

    public static JSONArray parseVariables(String variables) {
        return StringUtils.isBlank(variables) ? new JSONArray() : JSONArray.fromObject(variables);
    }

    private final String cloudName;
    private final String workspace;
    private final List<IInstanceProvider> instanceProviders;
    private final hudson.util.VariableResolver<String> resolver;
    private final EnvVars envVars;
    private final AbstractBuild build;
    private final Map<String, String> variableValueLookup;
    

    public VariableResolver(String cloudName, String workspace, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {
        this.cloudName = cloudName;
        this.workspace = workspace;
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
    
    public JSONObject resolve(JSONObject variable) throws IOException {
        String value = resolve(variable.getString("value"));
        variable.put("value", value);
        if ("Binding".equals(variable.getString("type"))) {
            if (value.startsWith("com.elasticbox.jenkins.builders.")) {
                for (IInstanceProvider instanceProvider : instanceProviders) {
                    if (value.equals(instanceProvider.getId())) {
                        variable.put("value", instanceProvider.getInstanceId(build));
                        break;
                    }
                }                
            }
            
            if (value.startsWith("(") && value.endsWith(")")) {
                Set<String> bindingTags = resolveTags(value.substring(1, value.length() - 1));
                JSONArray bindingInstances = DescriptorHelper.getInstances(bindingTags, cloudName, workspace, false);
                String errorMessage = null;
                if (bindingInstances.isEmpty()) {
                    errorMessage = MessageFormat.format("No instance found for binding variable {0} with the following tags: {1}",
                            variable.getString("name"), StringUtils.join(bindingTags, ", "));
                } else if (bindingInstances.size() > 1) {
                    errorMessage = MessageFormat.format("Binding ambiguity for binding variable {0} with the following tags: {1}, {2} instances are found with those tags.",
                            variable.getString("name"), StringUtils.join(bindingTags, ", "), bindingInstances.size());
                } else {
                    variable.put("value", bindingInstances.getJSONObject(0).getString("id"));
                }
                
                if (errorMessage != null) {
                    throw new IOException(errorMessage);
                }
            }            
        }

        if (variable.getString("scope").isEmpty()) {
            variable.remove("scope");
        }
            
        return variable;
    }
    
    public Set<String> resolveTags(String tags) {
        Set<String> tagSet = new HashSet<String>();
        if (StringUtils.isNotBlank(tags)) {
            for (String tag : tags.split(",")) {
                if (StringUtils.isNotBlank(tag)) {
                    tagSet.add(resolve(tag.trim()));
                }
            }
        }
        return tagSet;        
    }
    
    public JSONArray resolveVariables(String jsonVariables) throws IOException {
        JSONArray resolvedVariables = parseVariables(jsonVariables);
        for (Iterator iter = resolvedVariables.iterator(); iter.hasNext();) {
            resolve((JSONObject) iter.next());
        }
        
        return resolvedVariables;
        
    }
    
}
