[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/elasticbox-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/elasticbox-plugin/)

ElasticBox CI Plug-in 
=====================

The ElasticBox CI Jenkins plug-in provides full integration between Jenkins and ElasticBox (http://elasticbox.com).
With this plugin, Jenkins can launch, provision, and manage Jenkins slaves on-demand in different cloud providers via ElasticBox. It also provides build steps to deploy and manage your applications, including complex, multi-tiear applications that are defined as boxes in ElasticBox.

  - Configure project to be built on slaves deployed with specific deployment profile and variables in a specific workspace.
  - Slave can be single-use, single-use slaves cannot be reused for another build.
  - Limit maximum number of instances deployed in ElasticBox for current Jenkins server.
  - Retention time for slaves to be kept on after each build. A slave will be terminated and deleted after the retention time elapsed since its last build.
  - Build step to deploy box, update/reconfigure/reinstall/start/stop/terminate instances at large scale using tags.

Installation / Configuration
----------------------------

  - Jenkins must be restarted after installation of this plugin for the cron threads to be registered and started.
  - If you are starting a JNLP Jenkins slave agent in your slave instance and your Jenkins server is not wide-open at every port, configure a fixed JNLP port for your Jenkins server under Manager Jenkins > Configure Global Security.
  - Set Jenkins URL in configuration page of your Jenkins server with a host name or IP address that are accessible to the slaves.

How To Use
----------
See plugin wiki at https://wiki.jenkins-ci.org/display/JENKINS/ElasticBox+CI
