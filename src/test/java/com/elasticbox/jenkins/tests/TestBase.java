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

package com.elasticbox.jenkins.tests;

import com.elasticbox.jenkins.ElasticBoxCloud;
import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Phong Nguyen Le
 */
public class TestBase {

    protected ElasticBoxCloud cloud;
    
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    
    @Before
    public void createCloud() throws IOException {
        cloud = new ElasticBoxCloud("elasticbox", TestUtils.ELASTICBOX_URL, 2, 10, TestUtils.USER_NAME, TestUtils.PASSWORD, Collections.EMPTY_LIST);
        jenkins.getInstance().clouds.add(cloud);        
    }
    
}
