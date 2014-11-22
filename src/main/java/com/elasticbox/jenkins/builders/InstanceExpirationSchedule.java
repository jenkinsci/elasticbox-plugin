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
package com.elasticbox.jenkins.builders;

import com.elasticbox.Client;
import hudson.Extension;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import hudson.util.ListBoxModel;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Created by Phong Nguyen Le (ple@elasticbox.com) on 11/21/14.
 */
public abstract class InstanceExpirationSchedule extends InstanceExpiration {
    private static final DateFormat EBX_DATE_FORMAT = createStrictDateFormat("yyyy-MM-dd HH:mm:00.000000");
    public static final DateFormat DATE_FORMAT = createStrictDateFormat("MM/dd/yyyy");
    public static final DateFormat TIME_FORMAT = createStrictDateFormat("HH:mm");
    private static final DateFormat DATE_TIME_FORMAT = createStrictDateFormat("MM/dd/yyyy HH:mm");
    
    private static DateFormat createStrictDateFormat(String pattern) {
        DateFormat dateFormat = new SimpleDateFormat(pattern);
        dateFormat.setLenient(false);
        return dateFormat;
    }

    public static FormValidation checkTime(String value) {
        try {
            TIME_FORMAT.parse(value);
            return FormValidation.ok();
        } catch (ParseException ex) {
            return FormValidation.error("Time must be specified in format HH:mm with hour from 00 to 23");
        }
    }

    public static FormValidation checkDate(@QueryParameter String value) {
        try {
            DATE_FORMAT.parse(value);
            return FormValidation.ok();
        } catch (ParseException ex) {
            return FormValidation.error("Date must be specified in format MM/dd/yyyy");
        }
    }
    
    private final String operation;
    private final String hours;
    private final String date;
    private final String time;

    public InstanceExpirationSchedule(String operation, String hours, String date, String time) {
        this.operation = operation;
        this.hours = hours;
        this.date = date;
        this.time = time;
    }

    public String getOperation() {
        return operation;
    }

    public String getHours() {
        return hours;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }
    
    public String getUTCDateTime() throws ParseException {
        long currentTime = System.currentTimeMillis();
        Date dateTime = hours != null ? new Date(currentTime + TimeUnit.HOURS.toMillis(Integer.parseInt(hours))) :
                DATE_TIME_FORMAT.parse(MessageFormat.format("{0} {1}", date, time));
        return EBX_DATE_FORMAT.format(new Date(dateTime.getTime() - TimeZone.getDefault().getOffset(currentTime)));
    }
    
    public static abstract class InstanceExpirationScheduleDescriptor extends InstanceExpirationDescriptor {

        private ListBoxModel hoursItems;
        private ComboBoxModel times;

        public ListBoxModel doFillHoursItems() {
            if (hoursItems == null) {
                hoursItems = new ListBoxModel();
                hoursItems.add("1 hour", "1");
                hoursItems.add("6 hours", "6");
                hoursItems.add("12 hours", "12");
                hoursItems.add("24 hours", "24");
            }
            return hoursItems;
        }

        public ComboBoxModel doFillTimeItems() {
            if (times == null) {
                times = new ComboBoxModel();
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                for (int i = 0; i < 24 * 4; i++) {
                    times.add(TIME_FORMAT.format(calendar.getTime()));
                    calendar.add(Calendar.MINUTE, 15);
                }
            }
            return times;
        }

        public ComboBoxModel doFillDateItems() {
            ComboBoxModel dates = new ComboBoxModel();
            Calendar calendar = Calendar.getInstance();
            for (int i = 0; i < 30; i++) {
                dates.add(DATE_FORMAT.format(calendar.getTime()));
                calendar.add(Calendar.DATE, 1);
            }
            return dates;
        }
    }

    public static final class ShutDown extends InstanceExpirationSchedule {

        @DataBoundConstructor
        public ShutDown(String hours, String date, String time) {
            super(Client.InstanceOperation.SHUTDOWN, hours, date, time);
        }
        
        @Extension
        public static final class DescriptorImpl extends InstanceExpirationScheduleDescriptor {

            @Override
            public String getDisplayName() {
                return "Shutdown";
            }
            
        }
    }
    
    public static final class Terminate extends InstanceExpirationSchedule {

        @DataBoundConstructor
        public Terminate(String hours, String date, String time) {
            super(Client.InstanceOperation.TERMINATE, hours, date, time);
        }
        
        @Extension
        public static final class DescriptorImpl extends InstanceExpirationScheduleDescriptor {

            @Override
            public String getDisplayName() {
                return "Terminate";
            }
            
        }
    }
        
}
