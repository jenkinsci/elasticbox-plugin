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

package com.elasticbox.jenkins;

import com.elasticbox.Client;
import com.elasticbox.ClientException;
import hudson.model.Computer;
import hudson.model.Messages;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.HttpStatus;

/**
 *
 * @author Phong Nguyen Le
 */
final class ElasticBoxComputer extends SlaveComputer {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxComputer.class.getName());
    
    private boolean terminateOnOffline = false;
    private final ElasticBoxSlave slave;

    public ElasticBoxComputer(ElasticBoxSlave slave) {
        super(slave);
        this.slave = slave;
    }

    @Override
    public Future<?> disconnect(OfflineCause cause) {
        boolean online = isOnline();
        boolean terminateNow = false;
        if (cause instanceof OfflineCause.SimpleOfflineCause && 
                ((OfflineCause.SimpleOfflineCause) cause).description.toString().equals(Messages._Hudson_NodeBeingRemoved().toString())) {
            // remove any pending launches
            for (LabelAtom label : ElasticBoxLabelFinder.INSTANCE.findLabels(slave)) {
                for (NodeProvisioner.PlannedNode plannedNode : label.nodeProvisioner.getPendingLaunches()) {
                    if (plannedNode.displayName.equals(slave.getNodeName())) {
                        plannedNode.future.cancel(false);
                    }
                }
            }
            if (online) {
                terminateOnOffline = true;
            } else {
                terminateNow = true;
            }
        }

        Future<?> future = super.disconnect(cause);
        if (terminateNow) {
            terminate();
        }

        return future;
    }
    
    boolean mustBeTerminatedOnOffline() {
        return terminateOnOffline;
    }
    
    ElasticBoxSlave getSlave() {
        return slave;
    }

    public long getIdleTime() {
        return isIdle() && isOnline() ? System.currentTimeMillis() - getIdleStartMilliseconds() : 0;
    }

    void terminate() {
        if (slave.getInstanceUrl() == null) {
            return;
        }

        try {
            slave.checkInstanceReachable();
            if (slave.isSingleUse()) {
                for (Queue.BuildableItem item : Jenkins.getInstance().getQueue().getBuildableItems(this)) {
                    item.getFuture().cancel(true);
                }
            }
            disconnect(null);            
            boolean retry = false;
            try {
                slave.terminate();
            } catch (ClientException ex) {
                if (ex.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                    retry = true;
                    LOGGER.log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", slave.getDisplayName()), ex);
                }
            } catch (IOException ex) {
                retry = true;
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", slave.getDisplayName()), ex);
            }                

            if (retry) {
                Computer.threadPoolForRemoting.submit(new Runnable() {

                    public void run() {
                        for (int i = 0; i < 3; i++) {
                            try {
                                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
                                slave.terminate();
                                return;
                            } catch (IOException ex) {
                                LOGGER.log(Level.SEVERE, MessageFormat.format("Error termininating ElasticBox slave {0}", slave.getDisplayName()), ex);
                            } catch (InterruptedException ex) {
                            }
                        }
                        String instanceLocation = slave.getInstanceUrl();
                        try {
                            instanceLocation = Client.getPageUrl(slave.getCloud().getEndpointUrl(), instanceLocation);
                        } catch (IOException ex) {
                            Logger.getLogger(ElasticBoxSlave.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                        }
                        LOGGER.log(Level.SEVERE, MessageFormat.format("Cannot termininate ElasticBox slave {0} after several retries. Please terminate it manually at {1}", 
                                slave.getDisplayName(), instanceLocation));
                    }
                });
            }
        } catch (IOException ex) {    
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }                
    }

}
