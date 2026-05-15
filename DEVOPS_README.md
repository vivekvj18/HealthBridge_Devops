# 🏥 Health-Bridge: Enterprise DevOps Infrastructure

![DevOps](https://img.shields.io/badge/DevOps-Lifecycle-blue?style=for-the-badge)
![Kubernetes](https://img.shields.io/badge/kubernetes-%23326ce5.svg?style=for-the-badge&logo=kubernetes&logoColor=white)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
![Jenkins](https://img.shields.io/badge/jenkins-%232C5263.svg?style=for-the-badge&logo=jenkins&logoColor=white)
![ElasticSearch](https://img.shields.io/badge/-ElasticSearch-005571?style=for-the-badge&logo=elasticsearch)
![Vault](https://img.shields.io/badge/Vault-000000?style=for-the-badge&logo=vault&logoColor=white)
![Ansible](https://img.shields.io/badge/ansible-%231A1918.svg?style=for-the-badge&logo=ansible&logoColor=white)

This repository houses the advanced **DevOps infrastructure and CI/CD lifecycle** for **Health-Bridge**, a complex, polyglot microservices-based Health Information Exchange (HIE) platform built on HL7 FHIR standards. 

This project demonstrates production-grade DevOps practices, transitioning from basic containerization to a highly available, auto-scaling, and secure Kubernetes ecosystem with a fully automated Jenkins CI/CD pipeline.

---

## 🛠️ DevOps Technology Stack
* **Containerization:** Docker, Docker Compose
* **Orchestration:** Kubernetes (Minikube)
* **CI/CD Pipeline:** Jenkins (Declarative Pipeline, Monorepo Pattern)
* **Configuration Management:** Ansible
* **Secrets Management:** HashiCorp Vault
* **Centralized Logging:** EFK Stack (Elasticsearch, Filebeat, Kibana)
* **Version Control:** Git & GitHub

---

## 🚀 Architectural Evolution & Implementation

### 1. Base Containerization (Docker)
* **Microservices Dockerization:** Containerized 9 distinct Java Spring Boot microservices and frontend applications using optimized `Dockerfiles`.
* **Docker Compose:** Initially orchestrated the entire ecosystem (Microservices + PostgreSQL + MongoDB) using `docker-compose.yml` to establish a baseline for network bridging, environment variable injection, and local testing.

### 2. Configuration Management (Ansible)
* Developed **Ansible Playbooks and Roles** (`setup_cluster.yml`) to automate the provisioning and configuration of infrastructure prerequisites, ensuring a reproducible environment for CI/CD servers (Jenkins) and secret engines (Vault).

### 3. Advanced CI/CD Pipeline (Jenkins Monorepo Pattern)
Implemented a robust `Jenkinsfile` designed specifically for a Monorepo architecture containing multiple microservices.
* **Smart / Selective Build Strategy:** Instead of rebuilding the entire repository on every commit, the pipeline runs a Git diff (`git diff --name-only HEAD~1 HEAD`) to identify modified services. It only builds, tests, and deploys the *changed* microservices. 
  * *Impact:* Reduced CI/CD runtime from ~15 minutes to under 2 minutes per commit.
* **Automated Testing:** Integrated Maven Surefire plugin to run JUnit tests automatically, failing the build if tests do not pass, ensuring code reliability before deployment.
* **Docker Hub Integration:** Automated tagging and pushing of successful service images to a public Docker Hub repository.
* **Automated Polling:** Configured Jenkins Poll SCM to trigger the pipeline automatically upon detecting changes in the GitHub repository, establishing a true Continuous Deployment workflow.

### 4. Container Orchestration & High Availability (Kubernetes)
Migrated the entire architecture from Docker Compose to a local Kubernetes cluster (Minikube) to achieve enterprise-level resilience.
* **Zero-Downtime Deployments:** Leveraged Kubernetes `Deployment` manifests with rolling update strategies. Integrated `kubectl rollout restart` within the Jenkins pipeline to seamlessly update pods without application downtime.
* **Horizontal Pod Autoscaling (HPA):** Implemented HPA to dynamically scale microservices (e.g., API Gateway, Auth Service) based on CPU utilization metrics, ensuring the system can handle traffic spikes automatically.

### 5. Enterprise Security (HashiCorp Vault)
Hardcoded secrets in YAML files are a major security anti-pattern. 
* Integrated **HashiCorp Vault** directly into the Kubernetes cluster.
* Segregated sensitive credentials (e.g., PostgreSQL passwords, MongoDB URIs, JWT signing keys) into Vault's Key-Value (KV) secret engine.
* Configured services to securely fetch these credentials at runtime, adhering to DevSecOps best practices.

### 6. Centralized Logging & Monitoring (EFK Stack)
Debugging a distributed 15-pod microservices architecture requires centralized observability.
* **Architectural Pivot (Logstash to Filebeat):** Standard ELK stacks use Logstash, which is notoriously memory-heavy and caused `OOMKilled` (Out of Memory) crashes in our resource-constrained Kubernetes environment. We made a strategic architectural decision to pivot to an **EFK Stack**, replacing Logstash with **Filebeat** deployed as a DaemonSet.
* **Log Streaming & Visualization:** Filebeat efficiently ships logs directly from all container nodes to Elasticsearch. We configured **Kibana Dashboards** to visualize live log streams, making it trivial to trace FHIR validation failures, HIE exchange events, and Authentication flows across different hospitals.

---

## 💡 Key Takeaways & Challenges Overcome
1. **Handling Monorepo CI/CD:** Writing a dynamic Jenkins pipeline that iterates over an array of services and conditionally executes shell commands based on Git diffs was a major technical achievement.
2. **Resource Optimization in K8s:** Identifying Logstash as a memory bottleneck and replacing it with Filebeat demonstrated an understanding of system profiling and resource limits (`resources.requests` and `resources.limits`) in Kubernetes.
3. **Seamless K8s Integration:** Bridging the gap between the Jenkins build environment and the Minikube Docker daemon (`eval $(minikube docker-env)`) allowed for ultra-fast local deployments without relying on external registry pull rates.

---
*This DevOps architecture ensures the Health-Bridge platform is scalable, secure, observable, and continuously delivered with zero downtime.*
