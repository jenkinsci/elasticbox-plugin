#!/bin/bash

# Stop the agent
SLAVE_PID=$(cat slave.pid)
if [ -n ${SLAVE_PID} ]
then
    kill -9 ${SLAVE_PID}
fi
