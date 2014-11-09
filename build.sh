#/bin/bash
USAGE="Usage : build.sh -a ELASTICBOX_ADDRESS [-j JENKINS_VERSION,JENKINS_VERSION...]

Example:
    build.sh -a https://blue.elasticbox.com -j 1.509.3,1.581

Options:
    -a ElasticBox address to run tests against
    -j Jenkins versions to build with
    -p Package to upgrade ElasticBox appliance
    -t ElasticBox access token
    -w ElasticBox workspace
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
while getopts ":a:j:p:t:w:h" ARGUMENT
do
    case ${ARGUMENT} in

        a )  EBX_ADDRESS=$OPTARG;;
        j )  JENKINS_VERSIONS=$OPTARG;;
        p )  PACKAGE=$OPTARG;;
        t )  EBX_TOKEN=$OPTARG;;
        w )  EBX_WORKSPACE=$OPTARG;;
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

OLDEST_SUPPORTED_JENKINS_VERSION='1.532.1'
JENKINS_VERSION_COMMENT='version of Jenkins this plugin is built against'

function escape() {
    echo $@ | sed -e "s/\./\\\./g"
}

function set_jenkins_version() {
    sed -i -e "s|\(.*\)\(<version>.*</version>\)\(.*${JENKINS_VERSION_COMMENT}.*\)|\1<version>${1}</version>\3|" ${REPOSITORY_FOLDER}/pom.xml
}

function get_jenkins_version() {
    grep "${JENKINS_VERSION_COMMENT}" ${REPOSITORY_FOLDER}/pom.xml | sed -e "s|<version>\(.*\)</version>.*|\1|" -e "s/ //g"
}

function build_with_jenkins_version() {
    JENKINS_VERSION=${1}
    set_jenkins_version ${JENKINS_VERSION}
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
}

function upgrade_appliance() {
    echo Uploading package to ${EBX_ADDRESS}
    ADMIN_TOKEN=$(curl -k# -H 'Content-Type:application/json' -X POST --data '{"email": "test_admin@elasticbox.com", "password": "elasticbox"}' ${EBX_ADDRESS}/services/security/token)
    UPLOAD_URL="${EBX_ADDRESS}/services/appliance/upload"
    RESPONSE=$(curl -k# -X POST -H "ElasticBox-Token: ${ADMIN_TOKEN}" --form blob=@${PACKAGE} ${UPLOAD_URL})
    if [[ -n $(echo ${RESPONSE} | grep '"message"') ]]
    then
        echo Error uploading ${PACKAGE} to ${UPLOAD_URL}: ${RESPONSE}
        exit 1
    fi

    echo Start upgrading the appliance
    curl -ksf -X POST -H "ElasticBox-Token: ${ADMIN_TOKEN}" ${EBX_ADDRESS}/services/appliance/upgrade

    # Wait for the appliance services to restart
    sleep 30        

    # Make sure that the appliance is back up
    if [[ -z ${EBX_TOKEN} ]]
    then
        EBX_TOKEN=$(curl -k# -H 'Content-Type:application/json' -X POST --data '{"email": "test_admin@elasticbox.com", "password": "elasticbox"}' ${EBX_ADDRESS}/services/security/token)
    fi
    WORKSPACES=$(curl -k# -H "ElasticBox-Token: ${EBX_TOKEN}" ${EBX_ADDRESS}/services/workspaces | grep http://elasticbox.net/schemas/)
    if [[ -z "${WORKSPACES}" ]]
    then
        echo "Cannot access the ElasticBox appliance at ${EBX_ADDRESS} after upgrade" 
        exit 1
    fi
}

if [[ -n ${PACKAGE} ]]
then
    upgrade_appliance
fi

cd $(dirname $0)
REPOSITORY_FOLDER=$(pwd)

SAVED_JENKINS_VERSION=$(get_jenkins_version)

for VERSION in ${JENKINS_VERSIONS}
do
    build_with_jenkins_version ${VERSION}	
done

if [[ -z $(echo ${JENKINS_VERSIONS} | grep "${OLDEST_SUPPORTED_JENKINS_VERSION}") ]]
then
    build_with_jenkins_version ${OLDEST_SUPPORTED_JENKINS_VERSION}
    JENKINS_VERSIONS="${JENKINS_VERSIONS} ${OLDEST_SUPPORTED_JENKINS_VERSION}"
fi

if [[ -z $(echo ${JENKINS_VERSIONS} | grep "${SAVED_JENKINS_VERSION}") ]]
then
    build_with_jenkins_version ${SAVED_JENKINS_VERSION}
    JENKINS_VERSIONS="${JENKINS_VERSIONS} ${SAVED_JENKINS_VERSION}"
fi

LATEST_JENKINS_VERSION=$(curl -s http://repo.jenkins-ci.org/public/org/jenkins-ci/plugins/plugin/maven-metadata.xml | grep latest | sed -e "s|<latest>\(.*\)</latest>.*|\1|" -e "s/ //g")
if [[ -z $(echo ${JENKINS_VERSIONS} | grep "${LATEST_JENKINS_VERSION}") ]]
then
    build_with_jenkins_version ${LATEST_JENKINS_VERSION}
fi

if [[ -f "${REPOSITORY_FOLDER}/pom.xml-e" ]]
then
    mv -f ${REPOSITORY_FOLDER}/pom.xml-e ${REPOSITORY_FOLDER}/pom.xml
fi
