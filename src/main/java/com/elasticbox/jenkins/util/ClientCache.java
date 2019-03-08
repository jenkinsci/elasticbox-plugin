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

import com.elasticbox.Client;
import com.elasticbox.Client.HttpProxy;
import com.elasticbox.ClientException;
import com.elasticbox.jenkins.ElasticBoxCloud;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import hudson.slaves.Cloud;
import org.apache.http.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientCache {

    private static final Logger LOGGER = Logger.getLogger(ClientCache.class.getName());

    private static final ConcurrentHashMap<String, Client> clientCache = new ConcurrentHashMap<>();

    public static final Client findOrCreateClient(String cloudName) throws IOException {
        Client client = clientCache.get(cloudName);
        if (client != null) {
            return client;
        }

        synchronized (clientCache) {
            // remove clients of deleted clouds
            List<String> keysToRemove = new ArrayList<>();
            for (Enumeration<String> keys = clientCache.keys(); keys.hasMoreElements(); ) {
                String name = keys.nextElement();
                if (Jenkins.get().getCloud(name) == null) {
                    keysToRemove.add(name);
                }
            }
            for (String keyToRemove: keysToRemove) {
                clientCache.remove(keyToRemove);
            }

            Cloud cloud = Jenkins.get().getCloud(cloudName);
            if (cloud instanceof ElasticBoxCloud) {
                client = new CachedClient((ElasticBoxCloud) cloud, getHttpProxy());
                client.connect();
                clientCache.put(cloudName, client);
            } else if (StringUtils.isNotBlank(cloudName)) {
                throw new IOException(MessageFormat.format("Invalid cloud name ''{0}''", cloudName));
            }
        }

        return client;
    }

    public static HttpProxy getHttpProxy() {
        HttpProxy httpProxy = null;
        ProxyConfiguration proxyConfiguration = Jenkins.get().proxy;
        if ((proxyConfiguration != null) && (!StringUtils.isBlank(proxyConfiguration.name))) {
            httpProxy = new HttpProxy(proxyConfiguration.name, proxyConfiguration.port,
                    proxyConfiguration.getUserName(), proxyConfiguration.getPassword());
        }
        return httpProxy;
    }


    @CheckForNull
    public static final Client getClient(String cloudName) {
        try {
            return findOrCreateClient(cloudName);
        } catch (IOException ex) {
            LOGGER.log(
                    Level.SEVERE,
                    MessageFormat.format("Error creating client for ElasticBox cloud {0}", cloudName), ex);
        }

        return null;
    }

    public static Client getClient(String endpointUrl, String token) {
        for (Cloud cloud : Jenkins.get().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                ElasticBoxCloud ebCloud = (ElasticBoxCloud) cloud;
                if (ebCloud.getEndpointUrl().equals(endpointUrl)) {
                    if (StringUtils.isNotBlank(token)) {
                        if (token.equals(ebCloud.getToken())) {
                            return getClient(ebCloud.name);
                        }
                    }
                }
            }
        }

        return null;
    }

    public static void removeClient(ElasticBoxCloud cloud) {
        clientCache.remove(cloud.name);
    }

    private static final class CachedClient extends Client {
        private final String cloudName;

        public CachedClient(ElasticBoxCloud cloud, HttpProxy httpProxy) throws IOException {
            super(cloud.getEndpointUrl(), cloud.getToken(), httpProxy );
            cloudName = cloud.name;
        }

        private ElasticBoxCloud getElasticBoxCloud() {
            return (ElasticBoxCloud) Jenkins.get().getCloud(cloudName);
        }

        private void handleException(ClientException ex) {
            if (ex.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                clientCache.remove(cloudName);
            }
        }

        @Override
        public void connect() throws IOException {
            try {
                super.connect();
            } catch (ClientException ex) {
                handleException(ex);
                throw ex;
            }
        }

        @Override
        protected HttpResponse execute(HttpRequestBase request) throws IOException {
            try {
                return super.execute(request);
            } catch (ClientException ex) {
                handleException(ex);
                throw ex;
            }
        }
    }
}
