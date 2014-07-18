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

  - Create a Jenkins Slave box in ElasticBox (you can sign up at http://elasticbox.com and use it right away if you don't have an ElasticBox account yet). The box must have required variables `JENKINS_URL` and `JNLP_SLAVE_OPTIONS`. ElasticBox CI plugin will set the value of `JENKINS_URL` variable to the URL configured for this Jenkins server and fill the value of `JNLP_SLAVE_OPTIONS` automatically. Those variable are needed to download the slave agent from Jenkins server and start it from within the slave instance as shown in the following scripts. 
  
  Install event script
  ```sh
  #!/bin/bash

  # Download the slave agent from Jenkins server
  wget $JENKINS_URL/jnlpJars/slave.jar -O slave.jar  
  ```
  Start event script
  ```sh
  #!/bin/bash

  # Execute the slave agent and save the PID
  nohup java -jar slave.jar $JNLP_SLAVE_OPTIONS > /dev/null 2>&1 &
  echo \$! > slave.pid
  ```
  You also need to add the following command in the stop event script of the Jenkins Slave box to kill the slave agent process when the instance is shutting down
  ```sh
  #!/bin/bash

  # Stop the agent
  kill -9 $(cat slave.pid)
  ```
  
  - Configure slave to be provisioned on demand by clicking on Add button next to Slave Configurations in the ElasticBox cloud form.
  
  ![](https://wiki.jenkins-ci.org/download/attachments/72778254/slave-config.png)  

  Specify labels for the slave that any job can use to tie with slaves deployed with the configuration. The Environment is required and must be unique among slave configurations of the same ElasticBox cloud.

  - You also can configure your Jenkins project or job to use ElasticBox-managed slaves as following:
  
  ![](https://wiki.jenkins-ci.org/download/attachments/72778254/instance-creation.png)

  Select ElasticBox Single-Use Slave if you want to create a new instance for every execution of the job. After the job finished and the retention time elapsed, the instance will be terminated.
  
  - Add ElasticBox build steps to deploy, reconfigure, reinstall, stop, or terminate a box instance
    - **Deploy Box**

      ![](https://wiki.jenkins-ci.org/download/attachments/72778254/deploy.png)

      You can specify build parameters or build environment variables as value of the variables or environment of the box you select to deploy. In the above picture, the GitHub Pull Request build parameter ghprbSourceBranch is specified as value for the variable BRANCH to receive the Git source branch to be used during deployment of the box.
    
    - **Reconfigure Box**
    
      ![](https://wiki.jenkins-ci.org/download/attachments/72778254/reconfigure.png)
    
    - **Reinstall Box**
    
      ![](https://wiki.jenkins-ci.org/download/attachments/72778254/reinstall-existing.png)
    
    - **Start Box**: similarly to Reinstall Box, you can choose to start an existing box instance or an instance that will be deployed in one of the previous Deploy Box build step.
    
    - **Stop Box**: similarly to Reinstall Box, you can choose to start an existing box instance or an instance that will be deployed in one of the previous Deploy Box build step.
    
    - **Terminate Box**: similarly to Reinstall Box, you can choose to start an existing box instance or an instance that will be deployed in one of the previous Deploy Box build step. Additionally, you can select to delete the instance after its termination. 
    
      ![](https://wiki.jenkins-ci.org/download/attachments/72778254/terminate.png)
