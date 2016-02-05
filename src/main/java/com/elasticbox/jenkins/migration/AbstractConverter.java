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

package com.elasticbox.jenkins.migration;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import hudson.util.XStream2;
import java.util.List;

/**
 *
 * @author Phong Nguyen Le
 * @param <T>
 */
public abstract class AbstractConverter<T> extends XStream2.PassthruConverter<T> {

    public static abstract class Migrator<T> {
        private final Version version;

        public Migrator(Version version) {
            this.version = version;
        }

        protected abstract void migrate(T object, Version olderVersion);
    }

    private final List<? extends Migrator> migrators;

    public AbstractConverter(XStream2 xstream, List<? extends Migrator<T>> migrators) {
        super(xstream);
        this.migrators = migrators;
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Version version = getVersion(reader);
        for (Migrator migrator : migrators) {
            if (version.compareTo(migrator.version) < 0) {
                context.put(migrator, version);
            }
        }
        return super.unmarshal(reader, context);
    }

    @Override
    protected void callback(T object, UnmarshallingContext context) {
        for (Migrator migrator : migrators) {
            Version olderVersion = (Version) context.get(migrator);
            if (olderVersion != null) {
                migrator.migrate(object, olderVersion);
            }
        }
    }

    static Version getVersion(HierarchicalStreamReader reader) {
        String plugin = reader.getAttribute("plugin");
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
        return new Version(major, minor, micro);
    }

}
