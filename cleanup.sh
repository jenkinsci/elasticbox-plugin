#!/bin/bash

EBX_ADDRESS=$1
TOKEN=$2

function delete_boxes() {
	BOX_NAME_PART=${1}
	BOX_IDS=$(eb boxes list --token ${TOKEN} --address https://blue.elasticbox.com --no-keychain -i tphongio | grep ${BOX_NAME_PART} | awk '{ print $1 }')
	for BOX_ID in ${BOX_IDS}
	do
		BOX_URL=${EBX_ADDRESS}/services/boxes/${BOX_ID}
		echo Deleting box ${BOX_URL}	
		curl -k -X DELETE -H "ElasticBox-Token: ${TOKEN}" ${BOX_URL}	
	done
}

PROVIDER_IDS=$(curl -k -H "ElasticBox-Token: ${TOKEN}" ${EBX_ADDRESS}/services/workspaces/tphongio/providers | python -m json.tool | grep /services/providers | sed -e 's|/services/providers/||g' -e 's/"//g' | awk '{ print $2 }')
for PROVIDER_ID in ${PROVIDER_IDS}
do
	if [ ${PROVIDER_ID} != '345b6945-8834-4901-9090-d3e64535fd12' ]
	then
		PROVIDER_URL=${EBX_ADDRESS}/services/providers/${PROVIDER_ID}
		echo Deleting provider ${PROVIDER_URL}
		curl -k -X DELETE -H "ElasticBox-Token: ${TOKEN}" ${PROVIDER_URL}
	fi
done

delete_boxes test-deeply-nested-box-
delete_boxes test-nested-box-
delete_boxes test-linux-box-
delete_boxes test-binding-box-
