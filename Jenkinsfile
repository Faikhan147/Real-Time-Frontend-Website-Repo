pipeline {
    agent any

    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branch to build')
        string(name: 'REPO_URL', defaultValue: 'https://github.com/Faikhan147/Real-Time-Frontend-Website-Repo.git', description: 'Git repo URL')
        choice(name: 'ENVIRONMENT', choices: ['qa', 'staging', 'prod'], description: 'Select the environment to deploy')
    }

    environment {
        DOCKER_IMAGE = "faisalkhan35/my-website:${BUILD_NUMBER}"
        SLACK_WEBHOOK_URL = credentials('slack-webhook')
        FRONTEND_IMAGE_NAME = "faisalkhan35/my-website"
        TAG = "latest"
        SONAR_PROJECT_KEY = "Website"
        SONAR_PROJECT_NAME = "Frontend-Website"
        SONAR_SCANNER_HOME = "/opt/sonar-scanner"
        IMAGE_NAME_TAG = "${FRONTEND_IMAGE_NAME}:${TAG}"
        HELM_CHART_DIR = "helm"
        DOCKER_USER = credentials('dockerhub-credentials').username.toString()
        DOCKER_PASS = credentials('dockerhub-credentials').password.toString()
        WEBSITE_URL = credentials('website-url')
    }

    stages {

        stage('Checkout Code') {
            steps {
                checkout([ 
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.BRANCH}"]],
                    userRemoteConfigs: [[
                        url: "${params.REPO_URL}",
                        credentialsId: 'github-credentials'
                    ]]
                ])
            }
        }

        stage('SonarQube Code Analysis') {
            steps {
                withSonarQubeEnv('Sonar-Global-Token') {
                    dir('Website') {
                        script {
                            echo "Starting SonarQube scan..."
                            sh """
                                ${SONAR_SCANNER_HOME}/bin/sonar-scanner \
                                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                                -Dsonar.projectName=${SONAR_PROJECT_NAME} \
                                -Dsonar.sources=. \
                                -Dsonar.host.url=http://13.233.223.130:9000
                            """
                        }
                    }
                }
            }
        }

        stage('SonarQube Quality Gate Check') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    echo "Waiting for SonarQube Quality Gate..."
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Build and Scan in Parallel') {
            parallel {
                stage('Build Docker Image') {
                    steps {
                        dir('Website') {
                            script {
                                def commitHash = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                                echo "Building Docker image with commit hash: ${commitHash}"
                                sh """
                                    docker build --cache-from ${DOCKER_IMAGE}:${TAG} \
                                    --label commit=${commitHash} \
                                    -t ${IMAGE_NAME_TAG} . || { echo 'Docker build failed!'; exit 1; }
                                """
                            }
                        }
                    }
                }
                stage('Trivy Scan - Critical and High') {
                    steps {
                        echo "Starting Trivy scan for vulnerabilities..."
                        sh """
                            trivy image --exit-code 1 \
                            --severity CRITICAL,HIGH \
                            --format table \
                            --ignore-unfixed \
                            ${IMAGE_NAME_TAG} || { echo 'Trivy scan failed!'; exit 1; }
                        """
                    }
                }
            }
        }

        stage('Run Unit & Integration Tests') {
            when {
                expression { fileExists('Website/package.json') }
            }
            parallel {
                stage('Unit Tests') {
                    steps {
                        dir('Website') {
                            script {
                                echo "Running unit tests..."
                                sh """
                                    npm install || { echo 'npm install failed!'; exit 1; }
                                    npm run test -- --coverage --reporters=default --reporters=jest-html-reporter || { echo 'Unit tests failed!'; exit 1; }
                                """
                                publishHTML(target: [
                                    reportDir: 'Website',
                                    reportFiles: 'jest-html-report.html',
                                    reportName: 'Jest Test Report'
                                ])
                            }
                        }
                    }
                }
                stage('Integration Tests') {
                    steps {
                        dir('Website') {
                            script {
                                echo "Running integration tests..."
                                sh "npm run test:integration || { echo 'Integration tests failed!'; exit 1; }"
                            }
                        }
                    }
                }
            }
        }

        stage('DockerHub Login') {
            steps {
                script {
                    echo "Logging in to DockerHub..."
                    echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin || \
                    { echo 'DockerHub login failed!'; exit 1; }
                }
            }
        }

        stage('Push Docker Image with Retry') {
            steps {
                retry(3) {
                    echo "Pushing Docker image to DockerHub..."
                    sh "docker push ${IMAGE_NAME_TAG} || { echo 'Docker push failed!'; exit 1; }"
                }
            }
        }

        stage('Helm Lint and Test') {
            steps {
                script {
                    echo "Linting and testing Helm chart..."
                    sh """
                        helm lint ${HELM_CHART_DIR} || { echo 'Helm lint failed!'; exit 1; }
                        helm template website-${params.ENVIRONMENT} ${HELM_CHART_DIR} || { echo 'Helm template failed!'; exit 1; }
                    """
                }
            }
        }

        stage('AWS EKS Update Kubeconfig') {
            steps {
                script {
                    echo "Updating kubeconfig for EKS..."
                    sh 'aws eks update-kubeconfig --region ap-south-1 --name Faisal || { echo "Failed to update kubeconfig!"; exit 1; }'
                }
            }
        }

        stage('Deploy to QA/Staging with Helm') {
            when {
                expression { return params.ENVIRONMENT == 'qa' || params.ENVIRONMENT == 'staging' }
            }
            steps {
                script {
                    def chartValues = "image.repository=${DOCKER_IMAGE},image.tag=${BUILD_NUMBER},environment=${params.ENVIRONMENT}"
                    retry(3) {
                        echo "Deploying to ${params.ENVIRONMENT} environment..."
                        sh """
                            helm upgrade --install website-${params.ENVIRONMENT} ${HELM_CHART_DIR} \
                            --namespace ${params.ENVIRONMENT} \
                            --set ${chartValues} \
                            --set resources.requests.memory=128Mi \
                            --set resources.requests.cpu=100m \
                            --set resources.limits.memory=256Mi \
                            --set resources.limits.cpu=250m || { echo 'Helm deployment failed!'; exit 1; }
                        """
                    }
                }
            }
        }

        
