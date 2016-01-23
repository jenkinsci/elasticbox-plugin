package com.elasticbox.jenkins.model.repository.api;

import com.elasticbox.APIClient;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.api.criteria.box.CloudFormationPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.criteria.box.NoCloudFormationPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.criteria.box.NoPolicyAndNoApplicationBoxes;
import com.elasticbox.jenkins.model.repository.api.criteria.box.NoPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.factory.box.GenericBoxFactory;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            return  new NoPolicyAndNoApplicationBoxes().filter(boxesFromAPI);
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
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            return new NoPolicyBoxesJSONCriteria().filter(boxesFromAPI);
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
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            List<PolicyBox> policyBoxes = new CloudFormationPolicyBoxesJSONCriteria().filter(boxesFromAPI);
            return policyBoxes;
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
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            List<PolicyBox> policyBoxes = new NoCloudFormationPolicyBoxesJSONCriteria().filter(boxesFromAPI);
            return policyBoxes;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving boxes for this workspace: " + workspace + " from the API", e);
            throw new RepositoryException("Error retrieving no cloudformation policies boxes from API, workspace: "+workspace);
        }
    }

    @Override
    public AbstractBox getBox(String boxId) throws RepositoryException {
        try{
            JSONObject json = client.getBox(boxId);
            final AbstractBox box = new GenericBoxFactory().create(json);
            return box;
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
            JSONArray jsonArray = client.getBoxVersions(boxId);
            final GenericBoxFactory genericBoxFactory = new GenericBoxFactory();
            List<AbstractBox> boxVersions =  new ArrayList<>();
            for (Object jsonElement : jsonArray) {
                JSONObject jsonBox = (JSONObject)jsonElement;
                final AbstractBox abstractBox = genericBoxFactory.create(jsonBox);
                boxVersions.add(abstractBox);
            }
            return boxVersions;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "There is an error retrieving box versions for box: " + boxId, e);
            throw new RepositoryException("Error retrieving box versions for box: "+boxId);
        } catch (ElasticBoxModelException e) {
            logger.log(Level.SEVERE, "Error converting box version to boxes model for: "+boxId, e);
            throw new RepositoryException("Error converting box version to boxes model for: "+boxId);
        }

    }


}
