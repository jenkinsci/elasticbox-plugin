package com.elasticbox.jenkins.model.repository.api;

import com.elasticbox.APIClient;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.api.deserializer.filter.Filter;
import com.elasticbox.jenkins.model.repository.api.deserializer.filter.boxes.*;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes.BoxFactory;
import com.elasticbox.jenkins.model.repository.api.deserializer.transformer.boxes.PolicyBoxTransformer;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.elasticbox.jenkins.model.repository.api.deserializer.Utils.*;

/**
 * Created by serna on 11/26/15.
 */
    public class BoxRepositoryAPIImpl implements BoxRepository {

    private static final Logger logger = Logger.getLogger(BoxRepositoryAPIImpl.class.getName());

    private APIClient client;

    public BoxRepositoryAPIImpl(APIClient client) {
        this.client = client;
    }

    public List<AbstractBox> getNoPolicyAndNoApplicationBoxes(String workspace) throws RepositoryException {
        try {
            return transform(
                    filter(client.getAllBoxes(workspace),
                            new CompositeBoxFilter()
                                    .add(new BoxFilter())
                                    .add(new NoPolicyBoxesFilter())
                                    .add(new NoApplicationBoxes())
                            ),
                    new BoxFactory());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving boxes for this workspace: " + workspace + " from the API", e);
            throw new RepositoryException("Error retrieving no policies and no application boxes from API, workspace: "+workspace);
        }

    }

    /**
     * Returns the boxes that are not policy boxes
     * @return
     */
    @Override
    public List<AbstractBox> getNoPolicyBoxes(String workspace) throws RepositoryException {

        try {
            return transform(
                    filter(client.getAllBoxes(workspace),
                            new CompositeBoxFilter()
                                    .add(new BoxFilter())
                                    .add(new NoPolicyBoxesFilter())),
                    new BoxFactory());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving boxes for this workspace: " + workspace + " from the API", e);
            throw new RepositoryException("Error retrieving no policies boxes from API, workspace: "+workspace);
        }
    }

    /**e
     * Returns only the cloudformation template policy boxes
     * @return
     */
    @Override
    public List<PolicyBox> getCloudFormationPolicyBoxes(String workspace) throws RepositoryException {
        try{

            return transform(
                    filter(client.getAllBoxes(workspace),
                            new CompositeBoxFilter()
                                    .add(new BoxFilter())
                                    .add(new CloudFormationPolicyBoxesFilter())),
                    new PolicyBoxTransformer());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving boxes for this workspace: " + workspace + " from the API", e);
            throw new RepositoryException("Error retrieving cloudformation policies boxes from API, workspace: "+workspace);
        }
    }

    /**
     * Returns the rest of policy boxes that are not cloud formation policy boxes
     * @return
     */
    @Override
    public List<PolicyBox> getNoCloudFormationPolicyBoxes(String workspace) throws RepositoryException {
        try{

            return transform(
                    filter(client.getAllBoxes(workspace),
                            new CompositeBoxFilter()
                                    .add(new BoxFilter())
                                    .add(new NoCloudFormationPolicyBoxesFilter())),
                    new PolicyBoxTransformer());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving boxes for this workspace: " + workspace + " from the API", e);
            throw new RepositoryException("Error retrieving no cloudformation policies boxes from API, workspace: "+workspace);
        }
    }

    @Override
    public AbstractBox getBox(String boxId) throws RepositoryException {
        try{

            return new BoxFactory().apply(client.getBox(boxId));

        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving box: " + boxId + " from the API", e);
            throw new RepositoryException("Error retrieving box: "+boxId+" from API");
        } catch (ElasticBoxModelException e) {
            logger.log(Level.SEVERE, "Error converting box: \"+boxId+\" from JSON", e);
            throw new RepositoryException("Error converting box: "+boxId+" from JSON");
        }
    }

    @Override
    public List<AbstractBox> getBoxVersions(String boxId) throws RepositoryException {
        try{

            return transform(
                    client.getBoxVersions(boxId),
                        new BoxFactory());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving box versions for box: " + boxId, e);
            throw new RepositoryException("Error retrieving box versions for box: "+boxId);
        } catch (ElasticBoxModelException e) {
            logger.log(Level.SEVERE, "Error converting box version to boxes model for: "+boxId, e);
            throw new RepositoryException("Error converting box version to boxes model for: "+boxId);
        }

    }

    @Override
    public AbstractBox findBoxOrFirstByDefault(String workspace, final String box) throws RepositoryException {
        try {
            final JSONArray allBoxes = client.getAllBoxes(workspace);
            final List<JSONObject> filtered = filter(allBoxes, new CompositeBoxFilter()
                    .add(new BoxFilter())
                    .add(new Filter<JSONObject>() {
                        @Override
                        public boolean apply(JSONObject it) {
                            return it.getString("id").equals(box);
                        }
                    }));
            if(!filtered.isEmpty()){
                return new BoxFactory().apply(filtered.get(0));
            }

            return new BoxFactory().apply(allBoxes.getJSONObject(0));

        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving box:"+box+" for this workspace: " + workspace, e);
            throw new RepositoryException("There is an error retrieving box:"+box+" for this workspace: " + workspace);
        }
    }


}
