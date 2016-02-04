package com.elasticbox.jenkins.model.box;

import com.elasticbox.jenkins.model.AbstractModel;
import com.elasticbox.jenkins.model.member.Member;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 11/27/15.
 */
public class AbstractBox  extends AbstractModel {

    private String name;
    private String owner;
    private BoxType type;
    private Member[] members;

    public AbstractBox(ComplexBuilder builder) {
        super(builder.id);
        this.name = builder.name;
        this.type = builder.type;
        this.owner = builder.owner;
        this.members = builder.members;
    }

    public String getName() {
        return name;
    }

    public BoxType getType() {
        return type;
    }

    public String getOwner() {
        return owner;
    }

    public Member[] getMembers() {
        return members;
    }

    public interface Builder<T> {
        T build();
    }

    public boolean canWrite(String owner){
        if (getOwner().equals(owner))
            return true;

        for (Member member: getMembers()){
            if (member.getWorkspace().equals(owner)){
                if (member.getRole() == Member.Role.COLLABORATOR)
                    return true;
            }
        }
        return false;
    }

    public static abstract class ComplexBuilder<B extends ComplexBuilder<B,T>,T> implements Builder<T> {

        protected BoxType type;

        private String id;
        private String name;
        private String owner;
        private Member[] members;

        public B withId(String id){
            this.id = id;
            return getThis();
        }

        public B withName(String name){
            this.name =  name;
            return getThis();
        }

        public B withOwner(String owner){
            this.owner =  owner;
            return getThis();
        }

        public B withType(BoxType type){
            this.type =  type;
            return getThis();
        }

        public B withMembers(Member[] members){
            this.members =  members;
            return getThis();
        }

        @SuppressWarnings("unchecked")
        protected B getThis() {
            return (B) this;
        }

    }
}
