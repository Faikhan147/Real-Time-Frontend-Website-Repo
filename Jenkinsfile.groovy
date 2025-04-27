pipeline {
    agent any

    environment {
        FRONTEND_IMAGE_NAME = "faisalkhan35/my-website"
        TAG = "latest"
        KUBECONFIG = "/var/lib/jenkins/.kube/config"
        SONAR_PROJECT_KEY = "Website"
        SONAR_PROJECT_NAME = "Frontend-Website"
        SONAR_SCANNER_HOME = "/opt/sonar-scanner"
        IMAGE_NAME_TAG = "${FRONTEND_IMAGE_NAME}:${TAG}"
    }


        stage('SonarQube Code Analysis') {
            steps {
                withSonarQubeEnv('Sonar-Global-Token') {
                    dir('Website') {
                        script {
                            echo "🔍 Running sonar-scanner..."
                            sh "${SONAR_SCANNER_HOME}/bin/sonar-scanner \
                                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                                -Dsonar.projectName=${SONAR_PROJECT_NAME} \
                                -Dsonar.sources=. \
                                -Dsonar.host.url=http://13.126.239.35:9000"
                        }
                    }
                }
            }
        }

           // Quality Gate check (waits for the analysis result)
        stage('SonarQube Quality Gate Check') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Build Docker Image') {
            steps {
                dir('Website') {
                    sh "docker build -t ${IMAGE_NAME_TAG} ."
                }
            }
        }

        stage('Prod Trivy Scan - Critical Only') {
            steps {
                script {
                    echo "🚨 Scanning Docker image for CRITICAL vulnerabilities before production deployment"
                    sh """
                        trivy image --exit-code 1 \
                        --severity CRITICAL \
                        --format table \
                        --ignore-unfixed \
                        ${IMAGE_NAME_TAG}
                    """
                }
            }
        }

        stage('DockerHub Login') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin"
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                sh "docker push ${IMAGE_NAME_TAG}"
            }
        }

        stage('Deploy to EKS') {
            steps {
                sh 'kubectl apply -f Deployment.yaml'
                sh 'kubectl apply -f Service.yaml'
            }
        }
    }

    post {
        success {
            echo "✅ Deployment and Analysis Successful!"
        }
        failure {
            echo "❌ Build or Scan Failed. Please Check Logs."
        }
    }
}