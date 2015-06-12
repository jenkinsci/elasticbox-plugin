#!/bin/bash
apt-get -y update

# Install Git
if [ -x /usr/bin/yum ]; then
    yum install git -y
else
    apt-get install git -y
fi

# Install JDK
if [ -z $(which javac 2>/dev/null) ]
then
    apt-get -y install openjdk-7-jdk
    update-java-alternatives -s `update-java-alternatives -l | grep 1.7 | awk '{ print $1 }'`
fi

# Install Maven
if [ -z $(which mvn 2>/dev/null) ]
then
    apt-get -y install maven
fi
