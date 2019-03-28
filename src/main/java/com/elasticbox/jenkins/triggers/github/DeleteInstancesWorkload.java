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

package com.elasticbox.jenkins.triggers.github;

import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.jenkins.ElasticBoxExecutor;
import com.elasticbox.jenkins.util.ClientCache;

import hudson.Extension;
import hudson.model.TaskListener;

import net.sf.json.JSONObject;

import org.apache.http.HttpStatus;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

@Extension
public class DeleteInstancesWorkload extends ElasticBoxExecutor.Workload {
    private final Queue<PullRequestInstance> terminatingInstances = new ConcurrentLinkedQueue<PullRequestInstance>();

    public void add(PullRequestInstance terminatingInstance) {
        if (!terminatingInstances.contains(terminatingInstance)) {
            terminatingInstances.add(terminatingInstance);
        }
    }

    @Override
    protected ElasticBoxExecutor.ExecutionType getExecutionType() {
        return ElasticBoxExecutor.ExecutionType.ASYNC;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException {
        for (Iterator<PullRequestInstance> iter = terminatingInstances.iterator(); iter.hasNext();) {
            if (deleteInstance(iter.next(), listener)) {
                iter.remove();
            }
        }
    }

    private boolean deleteInstance(PullRequestInstance instance, TaskListener listener) {
        Client client = ClientCache.getClient(instance.cloud);

        JSONObject instanceJson;
        try {
            instanceJson = client.getInstance(instance.id);
        } catch (ClientException ex) {
            int exStatusCode = ex.getStatusCode() ;
            // SC_NOT_FOUND admitted for compatibility with previous versions to CAM 5.0.22033
            if ( (exStatusCode == HttpStatus.SC_FORBIDDEN) || (exStatusCode == HttpStatus.SC_NOT_FOUND)) {
                return true;
            }
            log(Level.SEVERE, MessageFormat.format(
                    "Error fetching instance {0}", client.getInstanceUrl(instance.id)), ex, listener);

            return false;
        } catch (IOException ex) {
            log(Level.SEVERE, MessageFormat.format(
                    "Error fetching instance {0}", client.getInstanceUrl(instance.id)), ex, listener);

            return false;
        }

        String state = instanceJson.getString("state");
        if (Client.InstanceState.UNAVAILABLE.equals(state)) {
            try {
                client.forceTerminate(instance.id);
            } catch (IOException ex) {
                log(Level.SEVERE,
                        MessageFormat.format(
                                "Error force-terminating instance {0}", client.getInstanceUrl(instance.id)),
                                ex,
                                listener);
            }
            return false;
        }
        if (!Client.InstanceState.DONE.equals(state)) {
            return false;
        }

        try  {
            try {
                client.delete(instance.id);
            } catch (ClientException ex) {
                if (ex.getStatusCode() == HttpStatus.SC_CONFLICT) {
                    return false;
                } else if (ex.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                    throw ex;
                }
            }
            return true;
        } catch (IOException ex) {
            log(Level.SEVERE,
                    MessageFormat.format(
                            "Error deleting instance {0}", client.getInstanceUrl(instance.id)), ex, listener);
            return false;
        }
    }

}
