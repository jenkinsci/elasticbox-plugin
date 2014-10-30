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

package com.elasticbox.jenkins;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import hudson.util.XStream2;
import jenkins.model.Jenkins;

/**
 * Zero is no longer can be used for retention time for unlimited time after version 0.9.2, so we need to replace 0 
 * with Integer.MAX_VALUE for any ElasticBox cloud, slave and slave configuration.
 * 
 * @author Phong Nguyen Le
 * @param <T> 
 */
public abstract class RetentionTimeConverter<T> extends XStream2.PassthruConverter<T> {
    static final String FIX_ZERO_RETENTION_TIME = "elasticbox.fixZeroRetentionTime";

    public RetentionTimeConverter() {
        super(Jenkins.XSTREAM2);
    }

    @Override
    public boolean canConvert(Class type) {
        return ElasticBoxCloud.class.isAssignableFrom(type) || ElasticBoxSlave.class.isAssignableFrom(type);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String plugin = reader.getAttribute("plugin");
        if (plugin != null && plugin.startsWith("elasticbox@")) {
            int end = plugin.endsWith("-SNAPSHOT") ? plugin.length() - "-SNAPSHOT".length() : plugin.length();
            String pluginVersion = plugin.substring("elasticbox@".length(), end);
            String[] versionParts = pluginVersion.split("\\.");
            int major = 0, minor = 0, micro = 0;
            if (versionParts.length > 0) {
                major = Integer.parseInt(versionParts[0]);
            }
            if (versionParts.length > 1) {
                minor = Integer.parseInt(versionParts[1]);
            }
            if (versionParts.length > 2) {
                micro = Integer.parseInt(versionParts[2]);
            }
            if (major == 0 && (minor < 9 || (minor == 9 && micro < 3))) {
                context.put(FIX_ZERO_RETENTION_TIME, Boolean.TRUE);
            }
        }
        return super.unmarshal(reader, context);
    }

    @Override
    protected void callback(T obj, UnmarshallingContext context) {
        if (context.get(FIX_ZERO_RETENTION_TIME) == Boolean.TRUE) {
            fixZeroRetentionTime(obj);
        }
    }

    protected abstract void fixZeroRetentionTime(T obj);
    
}
