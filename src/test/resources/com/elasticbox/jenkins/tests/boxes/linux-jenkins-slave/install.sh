#!/bin/bash
apt-get -y update

if [ -z $(which java 2>/dev/null) ]
then
    apt-get -y install openjdk-7-jre
fi

apt-get -y install git

# Download the Jenkins agent
wget $JENKINS_URL/jnlpJars/slave.jar -O slave.jar