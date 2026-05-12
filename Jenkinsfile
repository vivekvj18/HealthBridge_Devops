pipeline {
    agent any
    
    stages {
        stage('Check Tools') {
            steps {
                sh 'mvn --version'
                sh 'docker --version'
            }
        }
        stage('Build Project') {
            steps {
                sh 'mvn clean install'
            }
        }
        stage('Build All Docker Images') {
            steps {
                script {
                    // 1. Saari services ki ek list banayi
                    def services = [
                        'discovery-server',
                        'api-gateway-service',
                        'auth-identity-service',
                        'consent-service',
                        'hie-fhir-exchange-service',
                        'hospital-a-service',
                        'hospital-b-service',
                        'notification-audit-service',
                        'admin-reporting-service'
                    ]
                    
                    // 2. Loop chalaya har ek service ke liye
                    for (service in services) {
                        echo "Building image for: ${service}"
                        sh "cd services/${service} && docker build -t healthbridge-${service}:latest ."
                    }
                }
            }
        }
    }
}
