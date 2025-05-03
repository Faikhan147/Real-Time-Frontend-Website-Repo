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
        HELM_CHART_DIR = "helm/website-chart"
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

        stage('DockerHub Login') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    echo "Logging in to DockerHub..."
                    sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin || { echo 'DockerHub login failed!'; exit 1; }"
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
            // Namespace ko check aur create karna
            sh """
                kubectl get namespace ${params.ENVIRONMENT} || kubectl create namespace ${params.ENVIRONMENT}
            """
            
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


        stage('Approval for Production') {
            when {
                expression { return params.ENVIRONMENT == 'prod' }
            }
            steps {
                input message: "Deploy to Production?", ok: "Yes, deploy now"
            }
        }

stage('Deploy to Production with Helm') {
    when {
        expression { return params.ENVIRONMENT == 'prod' }
    }
    steps {
        script {
            // Namespace ko check aur create karna
            sh """
                kubectl get namespace prod || kubectl create namespace prod
            """
            
            def chartValues = "image.repository=${DOCKER_IMAGE},image.tag=${BUILD_NUMBER},environment=prod"
            
            retry(3) {
                echo "Deploying to Production..."
                sh """
                    helm upgrade --install website-prod ${HELM_CHART_DIR} \
                    --namespace prod \
                    --set ${chartValues} \
                    --set resources.requests.memory=128Mi \
                    --set resources.requests.cpu=100m \
                    --set resources.limits.memory=256Mi \
                    --set resources.limits.cpu=250m || { echo 'Production deployment failed!'; exit 1; }
                """
            }
        }
    }
}

