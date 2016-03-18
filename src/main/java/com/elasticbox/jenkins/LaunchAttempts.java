package com.elasticbox.jenkins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LaunchAttempts {
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxSlaveHandler.class.getName());
    private static final short MAX_ATTEMPTS = 3;

    private static class Attempt {
        private ElasticBoxSlave slave = null;
        private short number = 0;
        private boolean submitting = true;

        private Attempt(ElasticBoxSlave elasticBoxSlave) {
            slave = elasticBoxSlave;
            number = 1;
        }
    }

    private static Map<String, Attempt> launchInstancesAttempts = new HashMap<>();

    public static void addAttempt(String configId, ElasticBoxSlave slave) {
        Attempt oldAttempt = launchInstancesAttempts.get(configId);
        if (oldAttempt == null) {
            launchInstancesAttempts.put(configId, new Attempt(slave) );
        } else {
            oldAttempt.slave = slave;
            oldAttempt.number++;
            oldAttempt.submitting = true;
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Added attempt (Total=" + getAttemptsNumber(configId)
                    + ") to slave configuration: " + configId);
        }
    }

    public static short getAttemptsNumber(String configId) {
        Attempt attempt = launchInstancesAttempts.get(configId);
        return (attempt == null) ? 0 : attempt.number;
    }

    public static boolean maxAttemptsReached(String configId) {
        Attempt currentAttempt = launchInstancesAttempts.get(configId);
        return currentAttempt != null && currentAttempt.number >= MAX_ATTEMPTS;
    }

    public static void resetAttempts(String configId) {
        launchInstancesAttempts.remove(configId);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Removing slave configuration from launch attempts collection: " + configId);
        }
    }

    public static void attemptFinished(String configId) {
        Attempt currentAttempt = launchInstancesAttempts.get(configId);
        if (currentAttempt != null) {
            currentAttempt.submitting = false;
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Attempt finished for configId: " + configId);
        }
    }

    public static List<ElasticBoxSlave> getPendingSlaves() {
        ArrayList<ElasticBoxSlave> list = new ArrayList<>();
        for (Attempt attempt: launchInstancesAttempts.values() ) {
            if (attempt.number < MAX_ATTEMPTS && !attempt.submitting) {
                list.add(attempt.slave);
            }
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Slaves launched but not yet deployed correctly: " + list);
        }
        return list;
    }
}
