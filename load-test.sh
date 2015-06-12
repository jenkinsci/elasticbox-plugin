#/bin/bash
USAGE="Usage : load-test.sh -j JENKINS_URL

Example:
    load-test.sh -j http://localhost:8080/jenkins

Options:
    -j Jenkins server URL
    -n Number of builds per jobs
    -h Display this message
"

function help() {
    echo "${USAGE}"

    if [[ ${1} ]]
    then
        echo ${1}
    fi
}

# Handle options
while getopts ":j:n:h" ARGUMENT
do
    case ${ARGUMENT} in

        j )  JENKINS_URL=$OPTARG;;
        n )  BUILDS=$(($OPTARG));;
        h )  help; exit 0;;
        : )  help "Missing option argument for -$OPTARG"; exit 1;;
        ? )  help "Option does not exist: $OPTARG"; exit 1;;

    esac
done

if [[ -z "${JENKINS_URL}" ]]
then
    help "Jenkins server URL must be specified"
    exit 1
fi

if [[ ${BUILDS} -lt 1 ]]
then
    BUILDS=1
fi

function jenkins() {
    java -jar jenkins-cli.jar -s ${JENKINS_URL} "$@"
}

echo "Downloading jenkins-cli.jar"
curl ${JENKINS_URL}/jnlpJars/jenkins-cli.jar -o jenkins-cli.jar
for JOB in $(jenkins list-jobs enabled)
do
    echo "Scheduling ${BUILDS} builds of job ${JOB}"
    for (( i = 0; i < ${BUILDS}; i++ ))
    do
        jenkins build $JOB -p hash=$i
    done
done

