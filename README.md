[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/elasticbox-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/elasticbox-plugin/)

ElasticBox CI Plug-in
=====================

The ElasticBox CI Jenkins plug-in provides full integration between Jenkins and ElasticBox (http://elasticbox.com).
With this plugin, Jenkins can launch, provision, and manage Jenkins slaves on-demand in different cloud providers via ElasticBox.

  - Configure project to be built on slaves deployed with a specific deployment profile in a specific workspace.
  - Slave can be single-use, single-use slaves cannot be reused for another build.
  - Limit maximum number of instances deployed in ElasticBox for current Jenkins server
  - Retention time for slaves to be kept on after each build. A slave will be terminated and deleted after the retention time elapsed since its last build.

Installation / Configuration
----------------------------

  - Jenkins must be restarted after installation of this plugin for the cron threads to be registered and started.
  - If you are starting a JNLP Jenkins slave agent in your slave instance and your Jenkins server is not wide-open at every port, configure a fixed JNLP port for your Jenkins server under Manager Jenkins > Configure Global Security.
  - Set Jenkins URL in configuration page of your Jenkins server with a host name or IP address that are accessible to the slaves.

How To Use
----------
  - Add ElasticBox as a cloud in the configuration page of Jenkins. Use Test Connection to make sure the information specified is correct.

  ![](https://wiki.jenkins-ci.org/download/attachments/72778254/elasticbox-cloud.png)

  - Create a Jenkins Slave box in ElasticBox (you can sign up at http://elasticbox.com and use it right away if you don't have an ElasticBox account yet). The box must have required variables `JENKINS_URL` and `SLAVE_NAME`. ElasticBox CI plugin will set the value of `JENKINS_URL` variable to the URL configured for this Jenkins server, value of `SLAVE_NAME` to an auto-generated slave name. The box instance will use these variables in the start event scripts to start the slave agent as following:
  ```sh
  #!/bin/bash

  # Execute the agent and save the PID
  nohup java -jar slave.jar -jnlpUrl $JENKINS_URL/computer/$SLAVE_NAME/slave-agent.jnlp > /dev/null 2>&1 &
  echo \$! > slave.pid
  ```
  You also need to add the following command in the stop event script of the Jenkins Slave box to kill the slave agent process when the instance is shutting down
  ```sh
  #!/bin/bash

  # Stop the agent
  kill -9 $(cat slave.pid)
  ```
  - Configure your Jenkins project or job to use ElasticBox-managed slaves as following:
  
  ![](https://wiki.jenkins-ci.org/download/attachments/72778254/instance-creation.png)

  Select ElasticBox Single-Use Slave if you want to create a new instance for every execution of the job. After the job finished and the retention time elapsed, the instance will be terminated.

