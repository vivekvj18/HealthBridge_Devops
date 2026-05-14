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
                sh 'mvn clean install -DskipTests'
            }
        }
        stage('Run Tests') {
            steps {
                sh 'mvn test'
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
                                cd services/${service}
                                
                                # 1. Build and Push to Docker Hub (for Rubrics Requirement)
                                docker build -t 2001vivekjoshi/${service}:latest .
                                docker push 2001vivekjoshi/${service}:latest
                                
                                # 2. Build directly into Minikube for ultra-fast local deployment
                                eval \$(minikube docker-env)
                                docker build -t health-bridge/${service}:1.0 .
                            """
                            
                            def deploymentName = service.replace("-service", "")
                            if (service == 'api-gateway-service') deploymentName = 'api-gateway'
                            if (service == 'hie-fhir-exchange-service') deploymentName = 'hie-fhir-service'
                            if (service == 'auth-identity-service') deploymentName = 'auth-service'
                            
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
