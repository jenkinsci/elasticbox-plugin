package com.elasticbox.jenkins.model.repository.api.criteria;

import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.api.factory.ModelFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by serna on 11/26/15.
 */
public abstract class AbstractJSONCriteria<T> implements JSONCriteria<T> {

    private ModelFactory<T> factory;

    public AbstractJSONCriteria(ModelFactory<T> factory) {
        this.factory = factory;
    }

    abstract boolean fits(JSONObject jsonObject);

    @Override
    public List<T> fits(JSONArray array){
        List<T> matched = new ArrayList<>();
            for (Iterator iter = array.iterator(); iter.hasNext();) {
                try {
                    JSONObject jsonObject = (JSONObject) iter.next();
                    if(fits(jsonObject))
                        matched.add(factory.create(jsonObject));

                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (ElasticBoxModelException e) {
                    e.printStackTrace();
                }
            }
        return  matched;
    }

}
