#!/bin/bash

function ebx_token() {
    curl -ks -H 'Content-Type:application/json' -X POST --data '{"email": "'$1'", "password": "'$2'"}' ${EBX_ADDRESS}/services/security/token
}

function upgrade_appliance() {
    PACKAGE=${1}
    echo Uploading package to ${EBX_ADDRESS}
    ADMIN_TOKEN=$(ebx_token test_admin@elasticbox.com elasticbox)
    UPLOAD_URL="${EBX_ADDRESS}/services/appliance/upload"
    RESPONSE=$(curl -k# -X POST -H "ElasticBox-Token: ${ADMIN_TOKEN}" --form blob=@${PACKAGE} ${UPLOAD_URL})
    if [[ -n $(echo ${RESPONSE} | grep '"message"') ]]
    then
        echo Error uploading ${PACKAGE} to ${UPLOAD_URL}: ${RESPONSE}
        exit 1
    fi

    echo Start upgrading the appliance
    curl -ksf -X POST -H "ElasticBox-Token: ${ADMIN_TOKEN}" ${EBX_ADDRESS}/services/appliance/upgrade || true

    # Wait for the appliance services to restart
    sleep 30

    # Make sure that the appliance is back up
    if [[ -z ${EBX_TOKEN} ]]
    then
        EBX_TOKEN=$(ebx_token test_admin@elasticbox.com elasticbox)
    fi
    WORKSPACES=$(curl -k# -H "ElasticBox-Token: ${EBX_TOKEN}" ${EBX_ADDRESS}/services/workspaces | grep http://elasticbox.net/schemas/)
    if [[ -z "${WORKSPACES}" ]]
    then
        echo "Cannot access the ElasticBox appliance at ${EBX_ADDRESS} after upgrade"
        exit 1
    fi
}
