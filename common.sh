#!/bin/bash

ELASTICBOX_RELEASE="4.0"

function ebx_token() {
    curl -ksf -H 'Content-Type:application/json' -X POST --data '{"email": "'$1'", "password": "'$2'"}' ${EBX_ADDRESS}/services/security/token
}

function check_online() {
    curl -ksf -H "ElasticBox-Token: $1" -H "ElasticBox-Release: $2" $3/services/workspaces | grep "http://elasticbox.net/schemas/"
}

function update_appliance_ip() {
    EBX_ADDRESS=${1}
    EBX_TOKEN=${2}
    IP=${EBX_ADDRESS/#https:\/\//}

    [[ -z ${EBX_TOKEN} ]] && EBX_TOKEN=$(ebx_token test_admin@elasticbox.com elasticbox)

    echo "Generating appliance settings with correct hostname: ${IP}"

    EBX_SETTINGS_ADDR="${EBX_ADDRESS}/services/appliance/settings"
    curl -ksf -H "ElasticBox-Token: ${EBX_TOKEN}" ${EBX_SETTINGS_ADDR} > settings.json

    if [[ ! -f "settings.json" ]]
    then
        logger "ERROR: Cannot generate appliance settings for appliance at https://${INSTANCE.ADDRESS}"
        exit 1
    fi

    sed -e "s|\"hostname\": \"[a-z0-9\.:/]*\"|\"hostname\": \"${IP}\"|" settings.json > newSettings.json

    echo "New settings:"
    curl -kf -X POST -H 'Content-Type:application/json' -H "ElasticBox-Token: ${EBX_TOKEN}" --data @"newSettings.json" ${EBX_SETTINGS_ADDR}
    echo
    rm settings.json newSettings.json
}

function upgrade_appliance() {
    PACKAGE=${1}
    EBX_ADDRESS=${2}
    EBX_TOKEN=${3}

    echo "Uploading package to ${EBX_ADDRESS}"
    ADMIN_TOKEN=$(ebx_token test_admin@elasticbox.com elasticbox)
    UPLOAD_URL="${EBX_ADDRESS}/services/appliance/upload"
    RESPONSE=$(curl -k# -X POST -H "ElasticBox-Token: ${ADMIN_TOKEN}" -H "ElasticBox-Release: ${ELASTICBOX_RELEASE}" --form blob=@${PACKAGE} ${UPLOAD_URL})
    if [[ -n $(echo ${RESPONSE} | grep '"message"') ]]
    then
        echo Error uploading ${PACKAGE} to ${UPLOAD_URL}: ${RESPONSE}
        exit 1
    fi

    [[ -z ${EBX_TOKEN} ]] && EBX_TOKEN=${ADMIN_TOKEN}

    echo "Start upgrading the appliance..."
    curl -ksf -X POST -H "ElasticBox-Token: ${ADMIN_TOKEN}" -H "ElasticBox-Release: ${ELASTICBOX_RELEASE}" "${EBX_ADDRESS}/services/appliance/upgrade" || true

    echo "Waiting for the appliance services to restart..."

    # Make sure that the appliance is back up (polling for a while)
    set +e
    for i in `seq 20`
    do
      sleep 70
      echo "...checking if the appliance is back online "$i
      if [[ -n $(check_online ${EBX_TOKEN} ${ELASTICBOX_RELEASE} ${EBX_ADDRESS}) ]]
      then
        echo "Restart finished successfully. Appliance is online."
        set -e
        return
      fi
    done

    echo "Cannot access the ElasticBox appliance at ${EBX_ADDRESS} after upgrade"
    exit 1
}

JENKINS_VERSION_COMMENT='version of Jenkins this plugin is built against'

function get_jenkins_version() {
    REPOSITORY_FOLDER=$1

    grep "${JENKINS_VERSION_COMMENT}" ${REPOSITORY_FOLDER}/pom.xml | sed -e "s|<version>\(.*\)</version>.*|\1|" -e "s/ //g"
}

function update_pom() {
    POM_FILE=$1
    JENKINS_VERSION=$2
    FORK_COUNT=$3

    if [[ -z "${FORK_COUNT}" ]]
    then
        FORK_COUNT=2C
    fi

    sed -i.bak -e "s|\(.*\)\(<version>.*</version>\)\(.*${JENKINS_VERSION_COMMENT}.*\)|\1<version>${JENKINS_VERSION}</version>\3|" \
        -e "s|\(.*\)\(<forkCount>.*</forkCount>\)|\1<forkCount>${FORK_COUNT}</forkCount>|" ${POM_FILE}
}

function get_latest_jenkins_version() {
    curl -s http://repo.jenkins-ci.org/public/org/jenkins-ci/plugins/plugin/maven-metadata.xml | grep latest | sed -e "s|<latest>\(.*\)</latest>.*|\1|" -e "s/ //g"
}
