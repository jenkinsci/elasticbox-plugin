/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.member;

import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import org.apache.commons.lang.enums.*;

/**
 * Created by serna on 1/21/16.
 */
public class Member {

    public enum Role{
        READ("read"), COLLABORATOR("collaborator");

        private String value;
        Role(String value){
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Role findByValue(String member) throws ElasticBoxModelException {
            for(Role r : values()){
                if( r.getValue().equals(member)){
                    return r;
                }
            }
            throw new ElasticBoxModelException("There is no Member type for value: "+member);
        }
    }
    private Role role;
    private String workspace;

    public Member(Role role, String workspace) {
        this.role = role;
        this.workspace = workspace;
    }

    public Role getRole() {
        return role;
    }

    public String getWorkspace() {
        return workspace;
    }
}
