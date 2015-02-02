Command to trigger a pull request build:

curl -X POST -H "X-GitHub-Event: pull_request" --data-urlencode payload@/Users/ple/Repositories/elasticbox-plugin/src/test/resources/com/elasticbox/jenkins/tests/test-pull-request-opened.json http://10.0.0.205:8080/jenkins/elasticbox/

curl -X POST -H "X-GitHub-Event: issue_comment" --data-urlencode payload@/Users/ple/Repositories/elasticbox-plugin/src/test/resources/com/elasticbox/jenkins/tests/test-github-issue-comment-created.json http://localhost:8080/jenkins/elasticbox/

curl -X POST -H "X-GitHub-Event: pull_request" --data-urlencode payload@/Users/ple/Repositories/elasticbox-plugin/src/test/resources/com/elasticbox/jenkins/tests/test-private-pull-request-synchronize.json http://localhost:8080/jenkins/elasticbox/
