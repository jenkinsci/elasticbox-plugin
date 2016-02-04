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

import com.elasticbox.Constants;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.ElasticBoxComputer;
import com.elasticbox.jenkins.builders.IInstanceProvider;
import hudson.Util;
import hudson.model.*;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public final class VariableResolver {

    public static JSONArray parseVariables(String variables) {
        return StringUtils.isBlank(variables) ? new JSONArray() : JSONArray.fromObject(variables);
    }

    private final String cloudName;
    private final String workspace;
    private final AbstractBuild build;
    private final List<IInstanceProvider> instanceProviders;
    private final Map<String, String> variableValueLookup;

    public VariableResolver(AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {
        this(null, null, build, listener);
    }

    public VariableResolver(String cloudName, String workspace, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {
        this.cloudName = cloudName;
        this.workspace = workspace;
        instanceProviders = new ArrayList<IInstanceProvider>();
        for (Object builder : ((Project) build.getProject()).getBuilders()) {
            if (builder instanceof IInstanceProvider) {
                instanceProviders.add((IInstanceProvider) builder);
            }
        }
        this.build = build;
        variableValueLookup = new HashMap<String, String>();
        variableValueLookup.putAll(build.getBuildVariables());
        variableValueLookup.putAll(build.getEnvironment(listener));
        final Node builtOn = build.getBuiltOn();
        final Computer computer = builtOn.toComputer();
        variableValueLookup.put("SLAVE_HOST_NAME", computer.getHostName());
        if (computer instanceof ElasticBoxComputer) {
            variableValueLookup.put("SLAVE_HOST_ADDRESS", ((ElasticBoxComputer) computer).getHostAddress());
        }
    }

    public String resolve(String value) {
        return Util.replaceMacro(value, variableValueLookup);
    }

    public JSONObject resolve(JSONObject variable) throws IOException {
        String value = resolve(variable.getString("value"));
        variable.put("value", value);
        String type = variable.getString("type");
        if ("Binding".equals(type)) {
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
                if (variable.containsKey("value")) {
                    variable.remove("value");
                }
                variable.put("tags", bindingTags);
            }

            variable.put("visibility", Constants.PRIVATE_VISIBILITY);
        } else if ("File".equals(type)) {
            if (StringUtils.isNotBlank(value)) {
                variable.put("value", new File(value).toURI().toString());
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
