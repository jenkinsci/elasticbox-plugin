#!/bin/bash

if [[ $# -lt 1 ]]
then
	echo "Usage: cleanup.sh EBX_ADDRESS EBX_WORKSPACE TAG"
	exit 1
fi

ELASTICBOX_RELEASE=3
EBX_ADDRESS=${1}
EBX_WORKSPACE=${2}
TAG=${3}

function delete_boxes() {
	BOX_NAME_PART=${1}
	BOX_IDS=$(eb boxes list --token ${EBX_TOKEN} --address ${EBX_ADDRESS} --no-keychain -i ${EBX_WORKSPACE} -t ${TAG} | awk '{ print $1 }')
	for BOX_ID in ${BOX_IDS}
	do
		BOX_URL=${EBX_ADDRESS}/services/boxes/${BOX_ID}
		echo Deleting box ${BOX_URL}	
		curl -k# -X DELETE -H "ElasticBox-Token: ${EBX_TOKEN}" -H "ElasticBox-Release: ${ELASTICBOX_RELEASE}" ${BOX_URL}
	done
}

sudo pip install ebcli --upgrade

cd $(dirname $0)
REPOSITORY_FOLDER=$(pwd)
source ${REPOSITORY_FOLDER}/common.sh

EBX_TOKEN=$(ebx_token test_admin@elasticbox.com elasticbox)

PROVIDER_IDS=$(curl -k# -H "ElasticBox-Token: ${EBX_TOKEN}" -H "ElasticBox-Release: ${ELASTICBOX_RELEASE}" ${EBX_ADDRESS}/services/workspaces/${EBX_WORKSPACE}/providers | python -m json.tool | grep /services/providers | sed -e 's|/services/providers/||g' -e 's/"//g' | awk '{ print $2 }')
for PROVIDER_ID in ${PROVIDER_IDS}
do
    PROVIDER_URL=${EBX_ADDRESS}/services/providers/${PROVIDER_ID}
    PROVIDER_NAME=$(curl -k# -H "ElasticBox-Token: ${EBX_TOKEN}" -H "ElasticBox-Release: ${ELASTICBOX_RELEASE}" ${PROVIDER_URL} | python -m json.tool | grep '^    "name":' | sed -e 's/[ ",]//g' -e 's/name://')
	if [ -z $(echo ${EXCLUDED_PROVIDERS} | grep ${PROVIDER_NAME}) ]
	then
		echo Deleting provider ${PROVIDER_URL}
		curl -k# -X DELETE -H "ElasticBox-Token: ${EBX_TOKEN}" -H "ElasticBox-Release: ${ELASTICBOX_RELEASE}" ${PROVIDER_URL}
		echo
	fi
done

delete_boxes
