#!/bin/bash

if [[ $# -lt 1 ]]
then
	echo "Usage: cleanup.sh EBX_ADDRESS EBX_WORKSPACE TAG"
	exit 1
fi

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
		curl -k# -X DELETE -H "ElasticBox-Token: ${EBX_TOKEN}" ${BOX_URL}	
	done
}

sudo pip install ebcli --upgrade

cd $(dirname $0)
REPOSITORY_FOLDER=$(pwd)
source ${REPOSITORY_FOLDER}/common.sh

EBX_TOKEN=$(ebx_token test_admin@elasticbox.com elasticbox)

PROVIDER_IDS=$(curl -k# -H "ElasticBox-Token: ${EBX_TOKEN}" ${EBX_ADDRESS}/services/workspaces/${EBX_WORKSPACE}/providers | python -m json.tool | grep /services/providers | sed -e 's|/services/providers/||g' -e 's/"//g' | awk '{ print $2 }')
for PROVIDER_ID in ${PROVIDER_IDS}
do
	if [ -z $(echo ${EXCLUDED_PROVIDERS} | grep ${PROVIDER_ID}) ]
	then
		PROVIDER_URL=${EBX_ADDRESS}/services/providers/${PROVIDER_ID}
		echo Deleting provider ${PROVIDER_URL}
		curl -k# -X DELETE -H "ElasticBox-Token: ${EBX_TOKEN}" ${PROVIDER_URL}
		echo
	fi
done

delete_boxes
