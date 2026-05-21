pipeline {
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        DOCKERHUB_USERNAME = "${DOCKERHUB_CREDENTIALS_USR}"
    }

    stages {

        stage('Build Order Service') {
            steps {
                dir('order-service') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Build Inventory Service') {
            steps {
                dir('inventory-service') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Build API Gateway') {
            steps {
                dir('api-gateway') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                sh 'echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin'

                dir('order-service') {
                    sh 'docker build -t $DOCKERHUB_USERNAME/order-service:latest .'
                    sh 'docker push $DOCKERHUB_USERNAME/order-service:latest'
                }

                dir('inventory-service') {
                    sh 'docker build -t $DOCKERHUB_USERNAME/inventory-service:latest .'
                    sh 'docker push $DOCKERHUB_USERNAME/inventory-service:latest'
                }

                dir('api-gateway') {
                    sh 'docker build -t $DOCKERHUB_USERNAME/api-gateway:latest .'
                    sh 'docker push $DOCKERHUB_USERNAME/api-gateway:latest'
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully! All images pushed to Docker Hub.'
        }
        failure {
            echo 'Pipeline failed. Check the logs above.'
        }
    }
}
