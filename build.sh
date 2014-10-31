#/bin/bash
USAGE="Usage : build.sh -a ELASTICBOX_ADDRESS [-j JENKINS_VERSION,JENKINS_VERSION...]

Example:
    build.sh -a https://blue.elasticbox.com -j 1.509.3,1.581

Options:
    -a ElasticBox address to run tests against
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
while getopts ":a:j:h" ARGUMENT
do
    case ${ARGUMENT} in

        a )  EBX_ADDRESS=$OPTARG;;
        j )  JENKINS_VERSIONS=$OPTARG;;
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
    if [[ -n ${EBX_ADDRESS} ]]
    then
        echo Testing against ElasticBox at ${EBX_ADDRESS}
    fi
    
    cd ${REPOSITORY_FOLDER}
    mvn -Delasticbox.jenkins.test.ElasticBoxURL=${EBX_ADDRESS} -DskipTests=false clean install
    
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
