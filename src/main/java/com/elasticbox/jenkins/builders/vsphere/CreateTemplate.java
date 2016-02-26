/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.builders.vsphere;

import com.elasticbox.Client;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.DescriptorHelper;
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.JsonUtil;
import com.elasticbox.jenkins.util.TaskLogger;
import com.elasticbox.jenkins.util.VariableResolver;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.AbortException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;


public class CreateTemplate extends Builder {
    private static final Logger LOGGER = Logger.getLogger(CreateTemplate.class.getName());

    private final String cloud;
    private final String workspace;
    private final String instanceTags;
    private final String templateName;
    private final String provider;
    private final String datacenter;
    private final String datastore;
    private final String folder;
    private final String claimFilter;
    private final String policyName;
    private final String claims;

    @DataBoundConstructor
    public CreateTemplate(String cloud, String workspace, String instanceTags, String templateName, String provider,
            String datacenter, String folder, String datastore, String claimFilter, String policyName, String claims) {
        super();
        this.cloud = cloud;
        this.workspace = workspace;
        this.instanceTags = instanceTags;
        this.templateName = templateName;
        this.provider = provider;
        this.datacenter = datacenter;
        this.folder = folder;
        this.datastore = datastore;
        this.claimFilter = claimFilter;
        this.policyName = policyName;
        this.claims = claims;
    }

    public String getCloud() {
        return cloud;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getInstanceTags() {
        return instanceTags;
    }

    public String getProvider() {
        return provider;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public String getDatastore() {
        return datastore;
    }

    public String getFolder() {
        return folder;
    }

    public String getClaimFilter() {
        return claimFilter;
    }

    public String getPolicyName() {
        return policyName;
    }

    public String getClaims() {
        return claims;
    }

    public String getTemplateName() {
        return templateName;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        TaskLogger logger = new TaskLogger(listener);
        logger.info(MessageFormat.format("Executing ''{0}'' build step", getDescriptor().getDisplayName()));

        VariableResolver resolver = new VariableResolver(build, listener);
        Set<String> tags = resolver.resolveTags(instanceTags);
        JSONArray instances = DescriptorHelper.getInstances(tags, cloud, workspace, true);
        Client client = ClientCache.findOrCreateClient(cloud);
        if (!instances.isEmpty()) {
            List<String> instanceIDs = new ArrayList<String>();
            for (Object instance : instances) {
                instanceIDs.add(((JSONObject) instance).getString("id"));
            }
            instances = client.getInstances(workspace, instanceIDs);
        }
        // remove instances of different providers
        for (Iterator iter = instances.iterator(); iter.hasNext();) {
            JSONObject instance = (JSONObject) iter.next();
            if (!instance.getJSONObject("policy_box").getString("provider_id").equals(provider)) {
                iter.remove();
            }
        }
        if (instances.isEmpty()) {
            throw new AbortException(
                    MessageFormat.format("No instance is found in provider {0} with the following tags: {1}",
                    provider, StringUtils.join(tags, ", ")));

        } else if (instances.size() > 1) {
            throw new AbortException(
                    MessageFormat.format("{0} instances are found in provider {1} with the following tags: {2}. "
                                    +  "Specify tags that are unique for the instance.",
                    instances.size(), provider, StringUtils.join(tags, ", ")));
        }

        JSONObject instance = instances.getJSONObject(0);
        String instancePageUrl = client.getPageUrl(instance);
        String resolvedTempateName = resolver.resolve(templateName);

        logger.info("Creating vSphere template ''{0}'' from instance {1} "
                        + "with the following parameters: datacenter = {2}, folder = {3}, datastore = {4}",
                resolvedTempateName, instancePageUrl, datacenter, folder, datastore);

        IProgressMonitor taskMonitor = client.createTemplate(
                resolvedTempateName, instance, datacenter, folder, datastore);

        taskMonitor.waitForDone(-1);
        logger.info("Template ''{0}'' is created successfully", resolvedTempateName);

        logger.info("Syncing provider {0}", client.getProviderPageUrl(provider));
        IProgressMonitor monitor = client.syncProvider(provider);
        monitor.waitForDone(15);

        if (StringUtils.isBlank(policyName)) {
            Set<String> claimSet = resolver.resolveTags(claimFilter);
            logger.info("Looking for the deployment policies with claims: {0}", StringUtils.join(claimSet, ", "));
            List<JSONObject> policies = client.getPolicies(workspace, claimSet);
            for (Iterator<JSONObject> policyIterator = policies.iterator(); policyIterator.hasNext();) {
                JSONObject policy = policyIterator.next();
                if (!provider.equals(policy.getString("provider_id"))) {
                    policyIterator.remove();
                }
            }
            if (policies.isEmpty()) {
                throw new AbortException(
                        MessageFormat.format("No deployment policy for provider {0} "
                                        + "is found with the following claims: {1}",
                            client.getProviderPageUrl(provider),
                                StringUtils.join(claimSet, ", ")));
            }
            for (JSONObject policy : policies) {
                String policyPageUrl = client.getPageUrl(policy);
                logger.info("Updating deployment policy {0}", policyPageUrl);
                policy.getJSONObject("profile").put("template", resolvedTempateName);
                client.doUpdate(policy.getString("uri"), policy);

                logger.info(
                        "Deployment policy {0} is updated with vSphere template ''{1}''",
                        policyPageUrl,
                        resolvedTempateName);
            }
        } else {
            logger.info("Creating a new deployment policy with vSphere template ''{0}''", resolvedTempateName);
            JSONObject policy = instance.getJSONObject("policy_box");
            policy.remove("id");
            policy.remove("members");
            policy.put("owner", workspace);
            JSONArray variables = policy.getJSONArray("variables");
            for (Iterator iter = variables.iterator(); iter.hasNext();) {
                JSONObject variable = (JSONObject) iter.next();
                if ("MainBox".equals(variable.getString("name"))) {
                    iter.remove();
                }
            }
            policy.put("variables", variables);
            policy.getJSONObject("profile").put("template", resolvedTempateName);
            policy.put("name", resolver.resolve(policyName));
            policy.put("claims", JSONArray.fromObject(resolver.resolveTags(claims)));
            try {
                policy = client.createBox(policy);
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
            logger.info("Deployment policy {0} is created with vSphere template ''{1}''",
                        client.getPageUrl(policy),
                        resolvedTempateName);
        }

        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return false;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox - Create vSphere Template";
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.containsKey("policyAction")) {
                if ("update".equals(formData.getString("policyAction"))) {
                    if (StringUtils.isBlank(formData.getString("claimFilter"))) {
                        throw new FormException(
                                "Claims are required to find deployment policies to update", "claimFilter");
                    }
                    formData.remove("policyName");
                    formData.remove("claims");
                } else {
                    if (StringUtils.isBlank(formData.getString("policyName"))) {
                        throw new FormException("Name is required to create new deployment policy", "policyName");
                    }
                    formData.remove("claimFilter");
                }
            }
            return super.newInstance(req, formData);
        }

        public ListBoxModel doFillCloudItems() {
            return DescriptorHelper.getClouds();
        }

        public ListBoxModel doFillWorkspaceItems(@QueryParameter String cloud) {
            return DescriptorHelper.getWorkspaces(cloud);
        }

        public ListBoxModel doFillProviderItems(@QueryParameter String cloud, @QueryParameter String workspace) {
            ListBoxModel items = new ListBoxModel();
            Client client = ClientCache.getClient(cloud);
            if (client != null && StringUtils.isNotBlank(workspace)) {
                try {
                    for (Object provider : client.getProviders(workspace)) {
                        JSONObject providerJson = (JSONObject) provider;
                        if (providerJson.getString("icon").contains("vsphere")) {
                            items.add(providerJson.getString("name"), providerJson.getString("id"));
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            return items;
        }

        public ListBoxModel doFillDatacenterItems(@QueryParameter String cloud, @QueryParameter String provider) {
            ListBoxModel items = new ListBoxModel();
            items.add("Datacenter of the instance", StringUtils.EMPTY);
            Client client = ClientCache.getClient(cloud);
            if (client != null && StringUtils.isNotBlank(provider)) {
                try {
                    JSONObject providerJson = client.getProvider(provider);
                    Set<String> datacenterNames = new HashSet<String>();
                    for (Object service : providerJson.getJSONArray("services")) {
                        JSONObject serviceJson = (JSONObject) service;
                        for (Object datacenter : serviceJson.getJSONArray("datacenters")) {
                            JSONObject datacenterJson = (JSONObject) datacenter;
                            String datacenterName = datacenterJson.getString("name");
                            if (!datacenterNames.contains(datacenterName)) {
                                datacenterNames.add(datacenterName);
                                items.add(datacenterName, datacenterJson.getString("mor"));
                            }
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            return items;
        }

        public ListBoxModel doFillFolderItems(@QueryParameter String cloud, @QueryParameter String provider,
                @QueryParameter String datacenter) {
            ListBoxModel items = new ListBoxModel();
            items.add("Folder of the instance", StringUtils.EMPTY);
            Client client = ClientCache.getClient(cloud);
            if (client != null && StringUtils.isNotBlank(provider) && StringUtils.isNotEmpty(datacenter)) {
                try {
                    JSONObject providerJson = client.getProvider(provider);
                    JSONObject datacenterJson = null;
                    for (Object service : providerJson.getJSONArray("services")) {
                        datacenterJson = JsonUtil.find((JSONObject) service, "datacenters", "mor", datacenter);
                        if (datacenterJson != null) {
                            break;
                        }
                    }
                    if (datacenterJson != null) {
                        Map<String, JSONObject> morToFolderMap = new HashMap<String, JSONObject>();
                        for (Object folder : datacenterJson.getJSONArray("folders")) {
                            JSONObject folderJson = (JSONObject) folder;
                            folderJson.put("children", new JSONArray());
                            morToFolderMap.put(folderJson.getString("mor"), folderJson);
                        }
                        List<JSONObject> rootFolders = new ArrayList<JSONObject>();
                        for (JSONObject folder : morToFolderMap.values()) {
                            String parentMor = folder.getString("parent");
                            JSONObject parentFolder = morToFolderMap.get(parentMor);
                            if (parentFolder == null) {
                                rootFolders.add(folder);
                                if (parentMor.equals(datacenterJson.getString("mor"))) {
                                    folder.put("name", "Root");
                                }
                            } else {
                                JSONArray children = parentFolder.getJSONArray("children");
                                children.add(folder.getString("mor"));
                                parentFolder.put("children", children);
                            }
                        }
                        Stack<JSONObject> stack = new Stack();
                        for (JSONObject rootFolder : rootFolders) {
                            rootFolder.put("indent", StringUtils.EMPTY);
                            stack.push(rootFolder);
                        }
                        while (!stack.isEmpty()) {
                            JSONObject folder = stack.pop();

                            String indent = folder.getString("indent");

                            items.add(
                                    MessageFormat.format("{0} {1}", indent, folder.getString("name")),
                                    folder.getString("mor"));

                            JSONArray children = folder.getJSONArray("children");
                            for (int i = 0; i < children.size(); i++) {
                                JSONObject subFolder = morToFolderMap.get(children.getString(i));
                                subFolder.put("indent", indent + '-');
                                stack.push(subFolder);
                            }
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            return items;
        }

        public ListBoxModel doFillDatastoreItems(@QueryParameter String cloud, @QueryParameter String provider,
                @QueryParameter String datacenter) {
            ListBoxModel items = new ListBoxModel();
            items.add("Datastore of the instance", StringUtils.EMPTY);
            Client client = ClientCache.getClient(cloud);
            if (client != null && StringUtils.isNotBlank(provider) && StringUtils.isNotEmpty(datacenter)) {
                try {
                    JSONObject providerJson = client.getProvider(provider);
                    JSONObject datacenterJson = null;
                    for (Object service : providerJson.getJSONArray("services")) {
                        datacenterJson = JsonUtil.find((JSONObject) service, "datacenters", "mor", datacenter);
                        if (datacenterJson != null) {
                            break;
                        }
                    }
                    if (datacenterJson != null) {
                        for (Object datastore : datacenterJson.getJSONArray("datastores")) {
                            JSONObject datastoreJson = (JSONObject) datastore;
                            if (datastoreJson.has("is_cluster") && datastoreJson.getBoolean("is_cluster")) {
                                continue;
                            }
                            items.add(datastoreJson.getString("name"), datastoreJson.getString("mor"));
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            return items;
        }
    }
}
