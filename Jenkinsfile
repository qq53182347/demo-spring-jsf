node('maven-docker') {
    def gitProps = checkout scm
    def jdk = tool 'jdk17'
    stage("show tool versions") {
        echo "GIT_BRANCH=${gitProps.GIT_BRANCH}, GIT_COMMIT=${gitProps.GIT_COMMIT}"
        nodejs(nodeJSInstallationName: 'node14') {
            withEnv(["JAVA_HOME=$jdk", "PATH=$jdk/bin:${env.PATH}"]) {
                sh """
                npm --version
                node --version
                java -version
                docker version
                docker info
                """
            }
        }
    }
    stage("clean") {
        sh "./gradlew clean"
    }
    stage('Build jar') {
        ansiColor('xterm') {
            try {
                nodejs(nodeJSInstallationName: 'node14') {
                    withEnv(["JAVA_HOME=$jdk", "PATH=$jdk/bin:${env.PATH}", "HOST_FOR_SELENIUM=172.17.0.1"]) {
                        sh "./gradlew build"
                    }
                }
            } finally {
                junit 'build/test-results/*/*.xml'
                step([$class: 'JacocoPublisher'])
                archiveArtifacts(artifacts: 'build/screenshot/*')

                recordIssues enabledForFailure: true, tool: spotBugs(pattern: 'build/reports/spotbugs/*.xml')
                recordIssues enabledForFailure: true, tool: pmdParser(pattern: 'build/reports/pmd/*.xml')
                recordIssues enabledForFailure: true, tools: [mavenConsole(), java(), javaDoc()]
            }

        }
    }
    stage("build docker image") {
        sh """
        docker build -t demo-spring-jsf .
        """
    }
    stage("push docker image") {
        sh """
        docker login image-registry.openshift-image-registry.svc:5000 -p \$(cat /run/secrets/kubernetes.io/serviceaccount/token) -u unused
        docker tag demo-spring-jsf image-registry.openshift-image-registry.svc:5000/jenkins2/demo-spring-jsf:latest
        docker push image-registry.openshift-image-registry.svc:5000/jenkins2/demo-spring-jsf:latest
        """
    }
    stage("rollout") {
        sh """
        oc login --help https://172.30.0.1:443 --token=\$(cat /run/secrets/kubernetes.io/serviceaccount/token)
        oc project jenkins2
        oc rollout status dc/demo-spring-jsf -w
        """
    }
}
