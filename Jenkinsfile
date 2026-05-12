pipeline {
    agent any
    
    stages {
        stage('Checkout') {
            steps {
                // Workspace ko clean karo pehle
                deleteDir()
                // Seedha files copy kar lo terminal command se
                sh 'cp -a /home/vivekjoshi/Desktop/Health-Bridge/. .'
            }
        }
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
