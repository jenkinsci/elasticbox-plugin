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

import com.elasticbox.Client;
import com.elasticbox.jenkins.ElasticBoxCloud;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.sf.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Phong Nguyen Le
 */
public class TestBase {
    protected final List<JSONObject> objectsToDelete = new ArrayList<JSONObject>();
    protected ElasticBoxCloud cloud;

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
        
    @Before    
    public void setup() throws Exception {
        cloud = new ElasticBoxCloud("elasticbox", "ElasticBox", TestUtils.ELASTICBOX_URL, 2, TestUtils.ACCESS_TOKEN, Collections.EMPTY_LIST);
        jenkins.getInstance().clouds.add(cloud);        
    }
    
    @After
    public void tearDown() throws IOException {
        Client client = cloud.getClient();
        for (int i = objectsToDelete.size() - 1; i > -1; i--) {
            try {
                client.doDelete(objectsToDelete.get(i).getString("uri"));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    protected boolean deleteAfter(JSONObject object) {
        if (object.containsKey("uri")) {
            String uri = object.getString("uri");
            for (JSONObject json : objectsToDelete) {
                if (json.getString("uri").equals(uri)) {
                    return false;
                }
            }
            objectsToDelete.add(object);
            return true;
        } else {
            return false;
        }
    }
}
