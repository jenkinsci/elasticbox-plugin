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

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.ComputerPinger;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import hudson.util.RunList;

import jenkins.model.Jenkins;

import org.apache.commons.httpclient.HttpStatus;

import org.jenkinsci.remoting.RoleChecker;

import java.io.IOException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class ElasticBoxComputer extends SlaveComputer {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxComputer.class.getName());

    private boolean terminateOnOffline = false;

    // A reference to the slave is kept here so the computer still can access to it even after it has been removed and
    // getNode() returns null
    private final ElasticBoxSlave slave;

    private volatile String cachedHostAddress;
    private volatile boolean hostAddressCached;

    public ElasticBoxComputer(ElasticBoxSlave slave) {
        super(slave);
        this.slave = slave;
    }

    public String getHostAddress() throws IOException, InterruptedException {
        if (hostAddressCached) {
            return cachedHostAddress;
        }

        VirtualChannel channel = getChannel();
        if (channel == null) {
            return null;
        }

        for (Inet4Address ia : channel.call(new HostAddresses())) {
            try {
                if (ComputerPinger.checkIsReachable(ia, 3)) {
                    cachedHostAddress = ia.getHostAddress();
                    hostAddressCached = true;
                    break;
                } else {
                    LOGGER.fine(MessageFormat.format("{0} didn't respond to ping", ia.getHostAddress()));
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
            }
        }

        return cachedHostAddress;
    }

    @Override
    public Future<?> disconnect(OfflineCause cause) {
        boolean online = isOnline();
        boolean terminateNow = false;
        if (isSlaveRemoved(cause)) {
            try {
                LOGGER.info(
                        MessageFormat.format(
                                "Slave {0} is removed, its instance {1} will be terminated.",
                                slave.getNodeName(),
                                slave.getInstancePageUrl()));

            } catch (IOException ex) {
                LOGGER.warning(
                        MessageFormat.format(
                                "Slave {0} is removed, its instance cannot be terminated due to the following error:"
                                        + " {1}",
                                slave.getNodeName(),
                                ex.getMessage()));

            }
            // remove any pending launches
            Set<LabelAtom> slaveLabels = new HashSet<LabelAtom>(ElasticBoxLabelFinder.INSTANCE.findLabels(slave));
            if (slave.isSingleUse()) {
                AbstractSlaveConfiguration slaveConfig = slave.getSlaveConfiguration();
                if (slaveConfig != null) {
                    slaveLabels.add(ElasticBoxLabelFinder.getLabel(slaveConfig, true));
                }
                RunList builds = slave.getComputer().getBuilds();
                if (slave.getInstanceUrl() == null && !builds.isEmpty()) {
                    AbstractBuild build = (AbstractBuild) builds.iterator().next();
                    String buildTag;
                    try {
                        buildTag = build.getEnvironment(TaskListener.NULL).get("BUILD_TAG");
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                        buildTag = build.getDescription();
                    }
                    LOGGER.warning(
                            MessageFormat.format(
                                    "The build ''{0}'' will be cancelled because a slave cannot be launched for it.",
                                    buildTag));
                }
            }
            for (LabelAtom label : slaveLabels) {
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
        slave.markForTermination();
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
                    LOGGER.log(
                            Level.SEVERE,
                            MessageFormat.format(
                                    "Error termininating ElasticBox slave {0}",
                                    slave.getDisplayName()),
                            ex);
                }
            } catch (IOException ex) {
                retry = true;
                LOGGER.log(
                        Level.SEVERE,
                        MessageFormat.format(
                                "Error termininating ElasticBox slave {0}",
                                slave.getDisplayName()),
                        ex);
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
                                LOGGER.log(
                                        Level.SEVERE,
                                        MessageFormat.format(
                                                "Error termininating ElasticBox slave {0}",
                                                slave.getDisplayName()),
                                        ex);

                            } catch (InterruptedException ex) {
                                Logger.getLogger(ElasticBoxSlave.class.getName()).log(Level.SEVERE,ex.getMessage(), ex);
                            }
                        }
                        String instanceLocation = slave.getInstanceUrl();
                        try {
                            instanceLocation = Client.getPageUrl(slave.getCloud().getEndpointUrl(), instanceLocation);
                        } catch (IOException ex) {
                            Logger.getLogger(ElasticBoxSlave.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                        }
                        LOGGER.log(
                                Level.SEVERE,
                                MessageFormat.format(
                                        "Cannot termininate ElasticBox slave {0} after several retries. "
                                                + "Please terminate it manually at {1}",
                                        slave.getDisplayName(),
                                        instanceLocation));
                    }
                });
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    private static boolean isSlaveRemoved(OfflineCause cause) {
        return cause instanceof OfflineCause.SimpleOfflineCause
                && ((OfflineCause.SimpleOfflineCause) cause)
                            .description
                                .toString()
                                    .equals(Messages._Hudson_NodeBeingRemoved().toString());
    }

    private static class HostAddresses implements Callable<List<Inet4Address>, IOException> {

        public List<Inet4Address> call() throws IOException {
            List<Inet4Address> inetAddresses = new ArrayList<Inet4Address>();

            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni =  nis.nextElement();
                Enumeration<InetAddress> enumeration = ni.getInetAddresses();
                while (enumeration.hasMoreElements()) {
                    InetAddress ia =  enumeration.nextElement();
                    if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) {
                        inetAddresses.add((Inet4Address) ia);
                    }
                }
            }
            return inetAddresses;
        }

        public void checkRoles(RoleChecker rc) throws SecurityException {
        }

    }

    @Extension
    public static final class ComputerListenerImpl extends ComputerListener {

        @Override
        public void onOffline(Computer computer) {
            if (computer instanceof ElasticBoxComputer) {
                ElasticBoxComputer ebComputer = (ElasticBoxComputer) computer;
                if (ebComputer.mustBeTerminatedOnOffline()) {
                    ebComputer.terminate();
                }
            }
        }

        @Override
        public void onConfigurationChange() {
            for (Node node : Jenkins.getInstance().getNodes()) {
                if (node instanceof ElasticBoxSlave) {
                    ElasticBoxSlave slave = (ElasticBoxSlave) node;
                    if (slave.isDeletable()) {
                        SlaveComputer computer = slave.getComputer();
                        if (computer != null && computer.isAcceptingTasks()) {
                            slave.markForTermination();
                        }
                    }
                }
            }
        }


    }

}
