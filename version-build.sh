#/bin/bash

set -e

USAGE="Usage : version-build.sh -a ELASTICBOX_ADDRESS -j JENKINS_VERSION

Example:
    build.sh -a https://blue.elasticbox.com -j 1.581

Options:
    -a ElasticBox address to run tests against
    -j Jenkins version to build with
    -t ElasticBox access token
    -w ElasticBox workspace
    -c Fork count
    -g GitHub access token
    -? Display this message
"

cd $(dirname $0)
REPOSITORY_FOLDER=$(pwd)
source ${REPOSITORY_FOLDER}/common.sh

function help() {
    echo "${USAGE}"

    if [[ ${1} ]]
    then
        echo ${1}
    fi
}

# Handle options
while getopts ":a:j:t:w:c:g:h" ARGUMENT
do
    case ${ARGUMENT} in

        a )  EBX_ADDRESS=$OPTARG;;
        j )  JENKINS_VERSION=$OPTARG;;
        p )  PACKAGE=$OPTARG;;
        t )  EBX_TOKEN=$OPTARG;;
        w )  EBX_WORKSPACE=$OPTARG;;
        c )  FORK_COUNT=$OPTARG;;
        g )  GITHUB_TOKEN=$OPTARG;;
        h )  help; exit 0;;
        : )  help "Missing option argument for -$OPTARG"; exit 1;;
        ? )  help "Option does not exist: $OPTARG"; exit 1;;

    esac
done

if [[ -z ${EBX_ADDRESS} ]]
then
    help "ElasticBox address must be specified"
    exit 1
fi

if [[ -z ${JENKINS_VERSION} ]]
then
    JENKINS_VERSION=$(curl -s http://repo.jenkins-ci.org/public/org/jenkins-ci/plugins/plugin/maven-metadata.xml | grep latest | sed -e "s|<latest>\(.*\)</latest>.*|\1|" -e "s/ //g")
fi

update_pom ${REPOSITORY_FOLDER}/pom.xml ${JENKINS_VERSION} ${FORK_COUNT}

echo ------------------------------------------------
echo Building with Jenkins version ${JENKINS_VERSION}
echo ------------------------------------------------
echo Testing against ElasticBox at ${EBX_ADDRESS}

BUILD_OPTIONS="-DskipTests=false -Delasticbox.jenkins.test.ElasticBoxURL=${EBX_ADDRESS}"

if [[ -n ${EBX_TOKEN} ]]
then
    BUILD_OPTIONS="${BUILD_OPTIONS} -Delasticbox.jenkins.test.accessToken=${EBX_TOKEN}"
fi

if [[ -n ${EBX_WORKSPACE} ]]
then
    BUILD_OPTIONS="${BUILD_OPTIONS} -Delasticbox.jenkins.test.workspace=${EBX_WORKSPACE}"
fi

if [[ -n ${GITHUB_TOKEN} ]]
then
    BUILD_OPTIONS="${BUILD_OPTIONS} -Dcom.elasticbox.jenkins.test.GitHubAccessToken=${GITHUB_TOKEN}"
fi

cd ${REPOSITORY_FOLDER}
mvn ${BUILD_OPTIONS} clean install

# keep the test results and logs for the tested Jenkins version
TEST_RESULTS_FOLDER=${REPOSITORY_FOLDER}/results/${JENKINS_VERSION}
rm -rf ${TEST_RESULTS_FOLDER}
mkdir -p ${TEST_RESULTS_FOLDER}
cd target/surefire-reports
for FILE in $(ls)
do
    if [ -f "${FILE}" ]
    then
        cp ${FILE} ${TEST_RESULTS_FOLDER}/${JENKINS_VERSION}_${FILE}
    fi
done

if [[ -f "${REPOSITORY_FOLDER}/pom.xml.bak" ]]
then
    mv -f ${REPOSITORY_FOLDER}/pom.xml.bak ${REPOSITORY_FOLDER}/pom.xml
fi


