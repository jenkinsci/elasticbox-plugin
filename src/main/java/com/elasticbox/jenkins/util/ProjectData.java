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

package com.elasticbox.jenkins.util;

import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.Saveable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author Phong Nguyen Le
 */
public class ProjectData implements Saveable {
    private static final Logger LOGGER = Logger.getLogger(ProjectData.class.getName());
    
    private static final ConcurrentHashMap<AbstractProject, ProjectData> projectDataLookup = new ConcurrentHashMap<AbstractProject, ProjectData>();
    
    public static abstract class Datum {
        protected abstract void setProjectData(ProjectData projectData);
    }
    
    private final List<Datum> data;

    private final transient AbstractProject<?, ?> project;

    private ProjectData(AbstractProject<?, ?>  project) {
        this.project = project;
        this.data = new ArrayList<Datum>();
    }   

    public AbstractProject<?, ?> getProject() {
        return project;
    }
    
    public synchronized void save() throws IOException {
        getXmlFile(project).write(this);        
    }
    
    public <T extends Datum> T get(Class<T> type) {
        for (Datum datum : data) {
            if (type.isInstance(datum)) {
                return (T) datum;
            }
        }
        return null;
    }
    
    /**
     * Adds a new Datum to this ProjectData
     * 
     * @param datum the new datum to add
     * @return false if the datum is not added because a datum with the same class name already exists, true otherwise
     */
    public synchronized boolean add(Datum datum) {
        for (Object object : data) {
            if (object.getClass().getName().equals(data.getClass().getName())) {
                return false;
            }
        }
        datum.setProjectData(this);
        return data.add(datum);
    }

    private static XmlFile getXmlFile(AbstractProject<?, ?> project) throws IOException {
        return new XmlFile(Jenkins.XSTREAM, new File(project.getRootDir(), "elasticbox.xml"));        
    }        
    
    private static ProjectData load(AbstractProject<?, ?> project) throws IOException {
        XmlFile xmlFile = getXmlFile(project);
        if (xmlFile.exists()) {
            ProjectData projectData = (ProjectData) xmlFile.read();
            if (projectData.data != null) {
                for (Datum datum : projectData.data) {
                    datum.setProjectData(projectData);
                }
            }
            return projectData;
        }
        return null;
    }
    
    public static ProjectData getInstance(AbstractProject project, boolean create) throws IOException {
        ProjectData projectData = projectDataLookup.get(project);
        if (projectData == null && create) {
            ProjectData newProjectData = new ProjectData(project);
            projectDataLookup.putIfAbsent(project, newProjectData);
            projectData = projectDataLookup.get(project);
            if (projectData == newProjectData) {
                try {
                    projectData.save();
                } catch (IOException ex) {
                    projectDataLookup.remove(project);
                    projectData = null;
                    throw ex;
                }
            }
        }
        return projectData;
    }
    
    public static ProjectData removeInstance(AbstractProject project) {
        return projectDataLookup.remove(project);
    }
    
    @Initializer(after = InitMilestone.COMPLETED)
    public static void loadAll() {
        List<ProjectDataListener> listeners = Jenkins.getInstance().getExtensionList(ProjectDataListener.class);
        for (AbstractProject<?,?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
            ProjectData data = null;
            try {
                data = ProjectData.load(job);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
            if (data != null) {
                projectDataLookup.put(job, data);
                for (ProjectDataListener listener : listeners) {
                    try {
                        listener.onLoad(data);
                    } catch (Throwable ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }            
        }        
    }     
    
}
