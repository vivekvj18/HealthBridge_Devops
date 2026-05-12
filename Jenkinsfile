pipeline {
    agent any
    
    stages {
        stage('Check Tools') {
            steps {
                sh 'mvn --version'
                sh 'docker --version' // Ye bhi check kar lete hain
            }
        }
        stage('Build Project') {
            steps {
                sh 'mvn clean install'
            }
        }
        stage('Build Docker Image') {
            steps {
                // Auth service ke folder me ja kar image build karo
                sh 'cd services/auth-identity-service && docker build -t auth-service:latest .'
            }
        }
    }
}
