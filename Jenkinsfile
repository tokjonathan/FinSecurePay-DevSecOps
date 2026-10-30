// FinSecure Pay CI/CD Pipeline on Jenkins
// Checkout \Build \ Test \ Package \ Security Checks \ Container Build \ Image Scan \ Push \ Deploy
//
// Jenkins credentials this file expects (Manage Jenkins -> Credentials):
//   dockerhub-creds  : Username with password  (Docker Hub username + access token)
//   aks-kubeconfig   : Secret file             (kubeconfig for the AKS cluster)

pipeline {
    agent any

    parameters {
        string(name: 'ROLLBACK_TO_TAG', defaultValue: '',
                description: 'Leave EMPTY for a normal build. To roll back, enter an earlier image tag (e.g. 1.0.0-3): the build is skipped and that version is redeployed.')
    }

    // Checks GitHub for new commits every ~2 minutes and,if exists, build automatically.
    // (Using Polling instead of a webhook as I will be running Jenkins on my laptop not on a cloud server.)
    triggers {
        pollSCM('H/2 * * * *')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        IMAGE_REPO = 'grampap/finsecure'
        SONAR_ORG     = 'tokjonathan'
        SONAR_PROJECT = 'tokjonathan_FinSecurePay-DevSecOps'
        DEPLOYMENT = 'finsecure'   // Deployment name in k8s/deployment.yaml
        CONTAINER  = 'finsecure'   // container name inside that Deployment
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    if (params.ROLLBACK_TO_TAG?.trim()) {
                        env.IMAGE_TAG = params.ROLLBACK_TO_TAG.trim()
                        currentBuild.displayName = "#${env.BUILD_NUMBER} ROLLBACK to ${env.IMAGE_TAG}"
                    } else {
                        // Tag = version from pom.xml + build number, e.g. 1.0.0-7 (unique per build)
                        def pomVersion = sh(script: 'mvn -B -q -ntp help:evaluate -Dexpression=project.version -DforceStdout',
                                            returnStdout: true).trim()
                        env.IMAGE_TAG = "${pomVersion}-${env.BUILD_NUMBER}"
                        currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
                    }
                    echo "Image: ${env.IMAGE_REPO}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Build') {
            when { expression { !params.ROLLBACK_TO_TAG?.trim() } }
            steps {
                sh 'mvn -B -ntp clean compile'
            }
        }

        stage('Test') {
            when { expression { !params.ROLLBACK_TO_TAG?.trim() } }
            steps {
                // A failing test makes this exit non-zero -> pipeline stops, nothing is deployed
                sh 'mvn -B -ntp test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Package') {
            when { expression { !params.ROLLBACK_TO_TAG?.trim() } }
            steps {
                sh 'mvn -B -ntp package -DskipTests'
                archiveArtifacts artifacts: 'target/app.jar', fingerprint: true
            }
        }

        stage('Security Checks') {
            when { expression { !params.ROLLBACK_TO_TAG?.trim() } }
            steps {
                //    Static code analysis + quality gate (SonarCloud).
                //    sonar.qualitygate.wait=true makes this step FAIL if the SonarCloud
                //    quality gate fails (bugs, vulnerabilities).
                withCredentials([string(credentialsId: 'sonarcloud-token', variable: 'SONAR_TOKEN')]) {
                    sh '''
                        mvn -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                            -Dsonar.host.url=https://sonarcloud.io \
                            -Dsonar.organization=$SONAR_ORG \
                            -Dsonar.projectKey=$SONAR_PROJECT \
                            -Dsonar.token=$SONAR_TOKEN \
                            -Dsonar.qualitygate.wait=true \
                            -Dsonar.qualitygate.timeout=300
                    '''
                }

                // 2) Dependency vulnerability scan of pom.xml (Trivy)
                sh '''
                    # Full report (HIGH + CRITICAL), never fails the build
                    trivy fs --scanners vuln --skip-dirs target --severity HIGH,CRITICAL \
                        --exit-code 0 --output trivy-dependency-report.txt .
                    cat trivy-dependency-report.txt

                    # SECURITY GATE: fail if any CRITICAL vulnerability that has a fix
                    trivy fs --scanners vuln --skip-dirs target --severity CRITICAL --ignore-unfixed \
                        --exit-code 1 .
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-dependency-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Container Build') {
            when { expression { !params.ROLLBACK_TO_TAG?.trim() } }
            steps {
                // linux/amd64 because the AKS nodes are Intel/AMD, while my Mac is on ARM
                sh '''
                    docker build --platform linux/amd64 \
                        --build-arg APP_VERSION=$IMAGE_TAG \
                        -t $IMAGE_REPO:$IMAGE_TAG .
                '''
            }
        }

        stage('Image Scan') {
            when { expression { !params.ROLLBACK_TO_TAG?.trim() } }
            steps {
                // Scan before pushing, prevent vulnerable image from reaching the registry
                sh '''
                    trivy image --platform linux/amd64 --severity HIGH,CRITICAL \
                        --exit-code 0 --output trivy-image-report.txt $IMAGE_REPO:$IMAGE_TAG
                    cat trivy-image-report.txt

                    # SECURITY GATE
                    trivy image --platform linux/amd64 --severity CRITICAL --ignore-unfixed \
                        --exit-code 1 $IMAGE_REPO:$IMAGE_TAG
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-image-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Push') {
            when { expression { !params.ROLLBACK_TO_TAG?.trim() } }
            steps {
                // Token comes from the Jenkins credential store; Jenkins masks it in the log
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                usernameVariable: 'DH_USER',
                                                passwordVariable: 'DH_TOKEN')]) {
                    sh '''
                        echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin
                        docker push $IMAGE_REPO:$IMAGE_TAG
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: 'aks-kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        kubectl apply -f k8s/configmap.yaml -f k8s/service.yaml

                        # Rolling update to the new image (zero downtime: maxUnavailable 0)
                        kubectl set image deployment/$DEPLOYMENT $CONTAINER=$IMAGE_REPO:$IMAGE_TAG
                        kubectl annotate deployment/$DEPLOYMENT \
                            kubernetes.io/change-cause="Jenkins build #$BUILD_NUMBER deployed $IMAGE_TAG" --overwrite

                        # Wait for the rollout; if the new pods never become Ready, roll back automatically
                        if ! kubectl rollout status deployment/$DEPLOYMENT --timeout=180s; then
                            echo "Rollout FAILED - rolling back to the previous version"
                            kubectl rollout undo deployment/$DEPLOYMENT
                            kubectl rollout status deployment/$DEPLOYMENT --timeout=180s
                            exit 1
                        fi

                        kubectl get pods -l app=finsecure -o wide
                        kubectl rollout history deployment/$DEPLOYMENT

                        # Smoke test through the public load balancer
                        IP=$(kubectl get svc finsecure -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                        echo "Smoke test: http://$IP/health"
                        sleep 10
                        curl -fsS --connect-timeout 5 --retry 10 --retry-delay 6 --retry-all-errors "http://$IP/health"
                        echo
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
        }
        success {
            echo "SUCCESS: ${env.IMAGE_REPO}:${env.IMAGE_TAG} is live on AKS"
        }
        failure {
            echo "FAILED: nothing new was deployed (or the rollout was rolled back)"
            // Email via the Email Extension plugin.
            // '$DEFAULT_RECIPIENTS' is a plugin token
            // the address lives in Jenkins' system config, not in this repo for security.
            emailext(
                to: '$DEFAULT_RECIPIENTS',
                subject: "FAILED: ${env.JOB_NAME} ${currentBuild.displayName}",
                body: """<p><b>Pipeline FAILED</b> - nothing new was deployed.</p>
                        <p>Job: ${env.JOB_NAME}<br>Build: ${currentBuild.displayName}<br>
                        Image tag: ${env.IMAGE_TAG ?: 'n/a'}</p>
                        <p>Console: <a href="${env.BUILD_URL}console">${env.BUILD_URL}console</a></p>
                        <p>The build log is attached.</p>""",
                mimeType: 'text/html',
                attachLog: true,
                compressLog: true
            )
        }
        fixed {
            // Sent when a build succeeds after a previous failure
            emailext(
                to: '$DEFAULT_RECIPIENTS',
                subject: "FIXED: ${env.JOB_NAME} ${currentBuild.displayName}",
                body: """<p><b>Pipeline back to green.</b> ${env.IMAGE_REPO}:${env.IMAGE_TAG} is live on AKS.</p>
                        <p><a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>""",
                mimeType: 'text/html'
            )
        }
    }
}