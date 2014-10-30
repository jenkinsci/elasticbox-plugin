#!/bin/bash

# Execute the agent and save the PID
nohup java -jar slave.jar $JNLP_SLAVE_OPTIONS > slave.log 2>&1 &

echo \$! > slave.pid