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
        stage('Build Docker Images (Selective)') {
            steps {
                script {
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
                    
                    // 1. Pata karo ki kaun si files change hui hain
                    def changedFiles = []
                    try {
                        def diffOutput = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true).trim()
                        changedFiles = diffOutput.split('\n')
                        echo "Changed files in this commit: ${changedFiles}"
                    } catch (e) {
                        echo "Could not get git diff. Building all services as fallback."
                        // Agar diff fail ho jaye (jaise pehla build), to sabko build kar do
                        changedFiles = services.collect { "services/${it}/dummy" }
                    }
                    
                    // 2. Loop chalao aur check karo
                    for (service in services) {
                        // Check karo ki kya koi changed file is service ke folder me hai
                        def serviceChanged = changedFiles.any { it.startsWith("services/${service}/") }
                        
                        if (serviceChanged) {
                            echo ">>> Service ${service} has changes. Building image..."
                            sh "cd services/${service} && docker build -t healthbridge-${service}:latest ."
                        } else {
                            echo "--- Service ${service} has no changes. Skipping."
                        }
                    }
                }
            }
        }
    }
}
