pipeline {
    agent any
    
    stages {
        stage('Check Tools') {
            steps {
                sh 'mvn --version'
            }
        }
        stage('Build Project') {
            steps {
                sh 'mvn clean install'
            }
        }
    }
}
