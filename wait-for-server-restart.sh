#!/bin/bash

USAGE="Usage : wait-for-server-restart.sh -s SERVER_URL -d DELAY -n MAX_ATTEMPTS

Example:
    wait-for-server-restart.sh -s http://10.166.246.83:8080 -d 10 -n 8

Options:
    -s Server Url to run tests against. Example: http://192.168.127.234:8080
    -d Delay in each run (seconds)
    -n Number of runs or attempts to do
    -? Display this message
"

function help() {
    echo "${USAGE}"

    if [[ ${1} ]]
    then
        echo ${1}
    fi
}

# Handle options
while getopts ":s:d:n:h" ARGUMENT
do
    case ${ARGUMENT} in

        s )  SERVER_URL=$OPTARG;;
        d )  DELAY=$OPTARG;;
        n )  MAX_ATTEMPTS=$OPTARG;;
        h )  help; exit 0;;
        : )  help "Missing option argument for -$OPTARG"; exit 1;;
        ? )  help "Option does not exist: $OPTARG"; exit 1;;

    esac
done

if [[ -z ${SERVER_URL} ]]
then
    help "Server url must be specified."
    exit 1
fi

if [[ -z ${DELAY} ]]
then
    DELAY=10
fi

if [[ -z ${MAX_ATTEMPTS} ]]
then
    MAX_ATTEMPTS=3
fi

if [[ ${MAX_ATTEMPTS} == 0 ]]
then
    exit 0
fi

# echo "Waiting"
attempt_counter=0
until (curl --output /dev/null --silent --head --fail ${SERVER_URL}); do
        if [ ${attempt_counter} -eq ${MAX_ATTEMPTS} ];then
          echo " Max attempts ${MAX_ATTEMPTS} reached without response from url."
          exit 1
        fi

        printf '.'
        attempt_counter=$(($attempt_counter+1))
        sleep ${DELAY}
done

echo "Response obtained from server."
exit 0