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

/**
 *
 * @author Phong Nguyen Le
 */
public class Version implements Comparable<Version> {
    public static final Version _0_9_3 = new Version(0, 9, 3);
    public static final Version _4_0_3 = new Version(4, 0, 3);

    public final int major, minor, micro;

    public Version(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    public int compareTo(Version version) {
        int result = compare(this.major, version.major);
        if (result == 0) {
            result = compare(this.minor, version.minor);
            if (result == 0) {
                result = compare(this.minor, version.micro);
            }
        }
        return result;
    }

    public int compare(int a, int b) {
        if (a > b) {
            return 1;
        } else if (a < b) {
            return -1;
        } else {
            return 0;
        }
    }
}
