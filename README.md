# Health-Bridge: Microservices DevOpsification Project.

This project demonstrates the transition of the Health-Bridge healthcare system from a monolithic architecture to a distributed microservices architecture, fully automated with a production-grade DevOps pipeline.

## 🎯 Project Objective
To achieve a full score of **25/25** based on the Software Production Engineering (SPE) evaluation criteria by implementing advanced DevOps practices, including Infrastructure as Code, Containerization, CI/CD, Orchestration, Autoscaling, Secret Management, and Centralized Observability.

## 🏗️ System Architecture
Health-Bridge simulates a real-world Health Information Exchange (HIE) and FHIR data exchange platform.
- **Microservices:** 9 distinct services (Auth, Consent, Hospital A, Hospital B, HIE/FHIR, Notification, Admin Reporting, Gateway, Discovery).
- **Databases:**
  - **PostgreSQL:** Used for Identity, Consent, Hospital A, and Audit data (databases: `auth_identity_db`, `consent_db`, `notification_audit_db`, `fhir_main`).
  - **MongoDB:** Used for Hospital B Clinical Records (`hospital_b`).
- **Frontends:** React-based portals for Patients/Admins and Hospital staff.

---

## 🚀 DevOps Implementation Plan (For 25/25 Marks)

To secure all marks, including the advanced and innovation criteria, the following features are mapped out for implementation:

### 1. Configuration Management (Ansible) - *[Secures Advanced Marks]*
- **Tool:** Ansible Playbooks with Roles.
- **Implementation:** Automated setup of the host environment to ensure reproducibility.
- **Roles:**
  - `role/docker`: Installs Docker Engine.
  - `role/kubernetes`: Installs Minikube and kubectl.
  - `role/jenkins`: Installs Jenkins CI server.
  - `role/vault`: Installs HashiCorp Vault.

### 2. Containerization (Docker & Docker Compose)
- **Tool:** Docker & Docker Compose.
- **Implementation:** 
  - Multi-stage `Dockerfile`s for all Spring Boot services and React frontends.
  - A master `docker-compose.yml` in the root directory to spin up the foundation (Eureka, Postgres with all required DBs, MongoDB) and test the internal network before moving to Kubernetes.

### 3. Continuous Integration & Delivery (CI/CD)
- **Tool:** Jenkins.
- **Implementation:** 
  - Automated pipeline triggered by GitHub Webhooks.
  - Steps: Fetch Code ➡️ Run Tests ➡️ Build JARs ➡️ Build Docker Images ➡️ Push to Registry ➡️ Deploy to Kubernetes.

### 4. Advanced Secret Management (Vault) - *[Secures Advanced Marks]*
- **Tool:** HashiCorp Vault.
- **Implementation:** 
  - Centralized storage of sensitive credentials (DB passwords, JWT secrets).
  - Kubernetes pods fetch these secrets dynamically at runtime, removing them from property files and hardcoded manifests.

### 5. Orchestration & Live Patching - *[Secures Innovation Marks]*
- **Tool:** Kubernetes (Minikube).
- **Implementation:** 
  - **Live Patching:** Implementation of a `RollingUpdate` strategy in Kubernetes deployments. This ensures zero-downtime updates; new pods are verified healthy before old ones are terminated.

### 6. Dynamic Scalability (HPA) - *[Secures Advanced Marks]*
- **Tool:** Kubernetes Horizontal Pod Autoscaler (HPA).
- **Implementation:** 
  - Configured for high-traffic services like `api-gateway` and `hie-fhir-exchange`.
  - Automatically scales pods from 1 to 3 replicas based on CPU utilization exceeding 70%.

### 7. Centralized Observability
- **Tool:** ELK Stack (Elasticsearch, Logstash, Kibana).
- **Implementation:** 
  - Logstash aggregates logs from all microservices.
  - Kibana provides a dashboard to monitor FHIR transactions and system health.

---

## 🛠️ Current Implementation State

We are currently in **Milestone 2: Orchestrating Locally**.
1. **Foundation Ready:** We have created the `docker-compose.yml` in the root directory to spin up Eureka and the databases.
2. **Multi-DB Support:** A script at `scripts/init-db.sql` is configured to create all required PostgreSQL databases (`fhir_main`, `auth_identity_db`, `consent_db`) automatically on startup.

### How to Run the Current State:
1. Navigate to the project root.
2. Run the foundation services:
   ```bash
   docker compose up -d
   ```
3. Verify Eureka Dashboard at `http://localhost:9095`.
 
