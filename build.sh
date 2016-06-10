#/bin/bash

set -e

USAGE="Usage : build.sh -a ELASTICBOX_ADDRESS [-j JENKINS_VERSION,JENKINS_VERSION...]

Example:
    build.sh -a https://blue.elasticbox.com -j 1.509.3,1.581

Options:
    -a ElasticBox address to run tests against
    -j Jenkins versions to build with
    -p Package to upgrade ElasticBox appliance
    -t ElasticBox access token
    -w ElasticBox workspace
    -c Fork count
    -g GitHub access token
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
while getopts ":a:j:p:t:w:c:g:h" ARGUMENT
do
    case ${ARGUMENT} in

        a )  EBX_ADDRESS=$OPTARG;;
        j )  JENKINS_VERSIONS=$OPTARG;;
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

if [[ -n ${JENKINS_VERSIONS} ]]
then
    JENKINS_VERSIONS="$(echo ${JENKINS_VERSIONS} | sed -e s/,/\ /g)"
fi

cd $(dirname $0)
REPOSITORY_FOLDER=$(pwd)

source ${REPOSITORY_FOLDER}/common.sh

SAVED_JENKINS_VERSION=$(get_jenkins_version ${REPOSITORY_FOLDER})

function set_jenkins_version() {
    # work-around by disabling forking for version 1.532.2 for now until a way to fix error 'Failed to initialize exploded war'
    JENKINS_VERSION=${1}
    if [[ -z "${FORK_COUNT}" ]]
    then
        if [[ ${JENKINS_VERSION} == ${SAVED_JENKINS_VERSION} ]]
        then
            FORK_COUNT=0
        else
            FORK_COUNT=2C
        fi
    fi

    sed -i.bak -e "s|\(.*\)\(<version>.*</version>\)\(.*${JENKINS_VERSION_COMMENT}.*\)|\1<version>${JENKINS_VERSION}</version>\3|" \
        -e "s|\(.*\)\(<forkCount>.*</forkCount>\)|\1<forkCount>${FORK_COUNT}</forkCount>|" ${REPOSITORY_FOLDER}/pom.xml
}

function build_with_jenkins_version() {
    JENKINS_VERSION=${1}

    # work-around by disabling forking for oldest supported Jenkins version for now until there is a way to fix error 'Failed to initialize exploded war'
    if [[ -z "${FORK_COUNT}" ]]
    then
        if [[ ${JENKINS_VERSION} == ${SAVED_JENKINS_VERSION} ]]
        then
            FORK_COUNT=0
        else
            FORK_COUNT=2C
        fi
    fi

    BUILD_OPTIONS="-a ${EBX_ADDRESS} -j ${JENKINS_VERSION} -c ${FORK_COUNT}"

    if [[ -n ${EBX_TOKEN} ]]
    then
        BUILD_OPTIONS="${BUILD_OPTIONS} -t ${EBX_TOKEN}"
    fi

    if [[ -n ${EBX_WORKSPACE} ]]
    then
        BUILD_OPTIONS="${BUILD_OPTIONS} -w ${EBX_WORKSPACE}"
    fi

    if [[ -n ${GITHUB_TOKEN} ]]
    then
        BUILD_OPTIONS="${BUILD_OPTIONS} -g ${GITHUB_TOKEN}"
    fi

    echo "Building Jenkins version [${JENKINS_VERSION}] with options [${BUILD_OPTIONS}]"
    bash $(dirname $0)/version-build.sh ${BUILD_OPTIONS}
}

if [[ -n ${PACKAGE} ]]
then
    upgrade_appliance ${PACKAGE} ${EBX_ADDRESS} ${EBX_TOKEN}
fi


for VERSION in ${JENKINS_VERSIONS}
do
    build_with_jenkins_version ${VERSION}
done

if [[ -z $(echo ${JENKINS_VERSIONS} | grep "${SAVED_JENKINS_VERSION}") ]]
then
    build_with_jenkins_version ${SAVED_JENKINS_VERSION}
    JENKINS_VERSIONS="${JENKINS_VERSIONS} ${SAVED_JENKINS_VERSION}"
fi

# Remove the build with the last Jenkins version until we decide what to do with Saveable and JS errors
# LATEST_JENKINS_VERSION=$(get_latest_jenkins_version)
#if [[ -z $(echo ${JENKINS_VERSIONS} | grep "${LATEST_JENKINS_VERSION}") && -z $(echo ${JENKINS_VERSIONS} | grep latest) ]]
#then
#    build_with_jenkins_version ${LATEST_JENKINS_VERSION}
#fi
