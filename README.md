[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/elasticbox-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/elasticbox-plugin/)

Cloud Application Manager Plug-in
=====================

The **CenturyLink Cloud Application Manager** plug-in provides full integration between Jenkins and Cloud Application Manager (https://www.ctl.io/cloud-application-manager).  
With this plugin, Jenkins can launch, provision, and manage Jenkins slaves on-demand in different cloud providers via Cloud Application Manager.  
It also provides build steps to deploy and manage your applications, including complex, multi-tier applications that are defined as boxes in Cloud Application Manager.

  - Configure project to be built on slaves deployed with specific deployment profile and variables in a specific workspace.
  - Slave can be single-use. Single-use slaves cannot be reused for another build.
  - Limit maximum number of instances deployed in Cloud Application Manager for current Jenkins server.
  - Retention time for slaves to be kept on after each build. A slave will be terminated and deleted after the retention time elapsed since its last build.
  - Build step to deploy box, update/reconfigure/reinstall/start/stop/terminate instances at large scale using tags.

Installation / Configuration
----------------------------

  - Jenkins must be restarted after installation of this plugin for the cron threads to be registered and started.
  - If you are starting a JNLP Jenkins slave agent in your slave instance and your Jenkins server is not wide-open at every port, configure a fixed JNLP port for your Jenkins server under Manager Jenkins > Configure Global Security.
  - Set Jenkins URL in configuration page of your Jenkins server with a host name or IP address that are accessible to the slaves.
  - If necessary, configure your proxy under Manager Jenkins > Plugins > Advanced - HTTP Proxy Configuration and the plugin will use it to connect to Cloud Application Manager.

How To Use
----------
See plugin wiki at https://wiki.jenkins-ci.org/display/JENKINS/ElasticBox+CI
