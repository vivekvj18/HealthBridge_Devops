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
        stage('Build Docker Images & Deploy to K8s (Selective)') {
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
                    
                    // 1. Check changed files
                    def changedFiles = []
                    try {
                        def diffOutput = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true).trim()
                        changedFiles = diffOutput.split('\n')
                        echo "Changed files in this commit: ${changedFiles}"
                    } catch (e) {
                        echo "Could not get git diff. Building all services as fallback."
                        changedFiles = services.collect { "services/${it}/dummy" }
                    }
                    
                    // 2. First apply all base infrastructure YAMLs
                    echo "Applying Kubernetes manifests..."
                    sh 'kubectl apply -f k8s/'
                    
                    // 3. Build changed services directly in Minikube and restart their deployments
                    for (service in services) {
                        def serviceChanged = changedFiles.any { it.startsWith("services/${service}/") }
                        
                        if (serviceChanged) {
                            echo ">>> Service ${service} has changes. Building image and deploying..."
                            // We build as :1.0 to match the K8s YAML files
                            // eval $(minikube docker-env) ensures images are built inside Minikube
                            sh """
                                eval \$(minikube docker-env)
                                cd services/${service}
                                docker build -t health-bridge/${service}:1.0 .
                            """
                            
                            // Because image tag is still 1.0, we force K8s to pull/use the new image by restarting the deployment
                            // Note: Deployment names in K8s are mostly identical to service folder names, except api-gateway which is just api-gateway sometimes.
                            // Let's use labels or approximate names. Usually it's the folder name without "-service" or just the folder name.
                            def deploymentName = service.replace("-service", "")
                            if (service == 'api-gateway-service') deploymentName = 'api-gateway'
                            if (service == 'hie-fhir-exchange-service') deploymentName = 'hie-fhir-service'
                            
                            sh "kubectl rollout restart deployment ${deploymentName} || true"
                        } else {
                            echo "--- Service ${service} has no changes. Skipping."
                        }
                    }
                }
            }
        }
    }
}
