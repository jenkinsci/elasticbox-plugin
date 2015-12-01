package com.elasticbox.jenkins.model.box;

import com.elasticbox.jenkins.model.AbstractModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/27/15.
 */
public class AbstractBox  extends AbstractModel {

    private String name;
    private String owner;
    private BoxType type;

    public AbstractBox(String id, String name, BoxType boxType) {
        super(id);
        this.name = name;
        this.type = boxType;
    }

    public String getName() {
        return name;
    }

    public BoxType getType() {
        return type;
    }

    public boolean isApplicationBox(){
        if(this.getType() == BoxType.APPLICATION){
            return true;
        }
        return false;
    }

    public boolean isCloudFormationBox(){
        if(this.getType() == BoxType.CLOUDFORMATION){
            return true;
        }
        return false;
    }

    public boolean isDockerBox(){
        if(this.getType() == BoxType.DOCKER){
            return true;
        }
        return false;
    }

}
