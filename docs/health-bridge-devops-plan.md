f# health-bridge Microservices DevOpsification Plan

## Purpose

This version refactors the earlier plan to match the current project state as of 2026-05-09. It keeps the compulsory microservices direction required for the project, but removes or marks work that is already completed so the remaining phases are realistic.

## Current Baseline

health-bridge is currently a modular Spring Boot backend with three separate React/Vite frontends:

- `backend`
- `frontend`
- `frontend-hospital-a`
- `frontend-hospital-b`

The backend is not yet split into deployable microservices, but the domain has already been cleaned up in a way that helps the split.

## Work Already Completed

### Branding and project naming

- Project naming has been moved to `health-bridge` across backend metadata, frontend package names, and visible UI branding.

### Database baseline changes

- Hospital B has already been migrated from PostgreSQL/JPA to MongoDB.
- Hospital B now uses Mongo documents:
  - `hospital_b_op_consults`
  - `hospital_b_patients`
- Hospital B repositories already use Spring Data MongoDB.
- Hospital B datasource/JPA wiring has already been removed from the backend.

### Identity and patient model cleanup

- ABHA ID is now treated as the canonical patient identifier.
- The old global patient identity model has been replaced by:
  - `patients`
  - `hospital_patient_links`
- Local hospital patient IDs are now mapped to ABHA instead of acting like a shared global key.

### Consent and audit hardening

- Consent records now capture richer exchange metadata such as requester/provider hospital context, decision timestamps, expiry, and revocation timing.
- Exchange audit logging has been expanded so HIE pull/push activity is easier to trace.

### Test and seed stability

- The test profile now uses H2 for the relational datasources instead of depending on a developer's local PostgreSQL setup.
- Hospital B Mongo configuration is now explicit and isolated.
- The seed flow has been made idempotent so sample Mongo data does not duplicate on every restart.

## Current Architecture Snapshot

### Runtime shape today

- One Spring Boot backend
- Three frontend apps
- PostgreSQL for shared platform data
- PostgreSQL for Hospital A native clinical records
- MongoDB for Hospital B native clinical records

### Current databases

| Purpose | Technology | Current name |
| --- | --- | --- |
| Shared platform data: auth, identity, consent, hospital registry, audit, notifications | PostgreSQL | `fhir_main` |
| Hospital A native records | PostgreSQL | `hospital_a_db` |
| Hospital B native records | MongoDB | `hospital_b` |

### What is still missing

- No service extraction yet
- No API Gateway yet
- No Eureka Discovery Server yet
- No Dockerfiles yet
- No `docker-compose.yml` yet
- No Jenkins pipeline yet
- No Kubernetes manifests yet
- No ELK stack yet
- No Actuator-based health probes yet

## Target Microservices Architecture

The final target should still follow the faculty requirement and the SPE report style: microservices, API Gateway, service discovery, containerization, CI/CD, Kubernetes, and centralized logging.

Recommended monorepo layout:

- `libs/health-bridge-common`
- `services/discovery-server`
- `services/api-gateway-service`
- `services/auth-identity-service`
- `services/consent-service`
- `services/hospital-a-service`
- `services/hospital-b-service`
- `services/hie-fhir-exchange-service`
- `services/notification-audit-service`
- `services/admin-reporting-service`

Recommended service ownership:

| Service | Owns | Target database |
| --- | --- | --- |
| Discovery Server | service registration | none |
| API Gateway | frontend-facing routing | none |
| Auth/Identity | auth, registration, patient profile, ABHA mapping, hospital registry | PostgreSQL `auth_identity_db` |
| Consent | consent lifecycle and consent token logic | PostgreSQL `consent_db` |
| Hospital A | Hospital A native consults and FHIR mapping | PostgreSQL `hospital_a_db` |
| Hospital B | Hospital B native consults and FHIR mapping | MongoDB `hospital_b` |
| HIE/FHIR Exchange | cross-hospital orchestration and FHIR validation | none |
| Notification/Audit | notifications and exchange audit trails | PostgreSQL `notification_audit_db` |
| Admin Reporting | admin dashboards and aggregation APIs | none or read-only cache later |

Important note:
Hospital B's MongoDB move is already done in the current backend and should be reused during service extraction, not redesigned from scratch.

## Updated Gap Analysis

### Already done

- `health-bridge` rename
- Hospital B MongoDB migration
- ABHA-first patient identity model
- stronger consent and audit metadata
- H2-backed relational test profile
- idempotent seed behavior

### Still to implement

- service extraction from the monolith
- API Gateway and Eureka
- database-per-service separation for shared platform modules
- containerization
- compose-based local environment
- Jenkins CI/CD
- Kubernetes deployment
- ingress
- centralized logging
- deployment smoke tests

## Remaining Implementation Phases

### Phase 0: Completed Foundation

This phase is already done and should be treated as the baseline for the remaining work:

- health-bridge naming and branding cleanup
- Hospital B MongoDB migration
- patient identity schema cleanup around ABHA
- consent/audit metadata hardening
- stable local/test config cleanup

### Phase 1: Service Boundary Freeze

Before splitting the backend, freeze the existing API behavior and extract clear ownership boundaries.

Deliverables:

- document current external endpoints and response shapes
- identify package-to-service mapping for each current backend module
- create `libs/health-bridge-common` for shared DTOs, security constants, error models, and FHIR helper classes
- define internal service-to-service APIs under `/internal/**`
- add Spring Boot Actuator to the current backend and future services
- introduce environment-specific property files:
  - `application-local.properties`
  - `application-docker.properties`
  - optional `application-k8s.properties`

### Phase 2: Create the Microservice Skeletons

Create empty but runnable service applications first, without moving all business logic at once.

Deliverables:

- discovery server
- API Gateway
- Auth/Identity service
- Consent service
- Hospital A service
- Hospital B service
- HIE/FHIR Exchange service
- Notification/Audit service
- Admin Reporting service

Technical notes:

- use Maven multi-module structure
- add Spring Cloud dependencies compatible with the current Spring Boot version
- preserve the current frontend-facing route structure through the Gateway
- keep frontends separate exactly as they are now

### Phase 3: Extract Platform Services

Move the shared business capabilities out of the current monolith first.

Move to Auth/Identity:

- login and JWT handling
- registration and patient onboarding
- patient profile and hospital link ownership
- hospital registry

Move to Consent:

- consent request lifecycle
- consent decision and revoke flows
- consent token management

Move to Notification/Audit:

- patient push notifications
- exchange audit logs

Important transition rule:
During this phase, keep the API contract stable for the frontends even if the internals are changing behind the Gateway.

### Phase 4: Extract Hospital A, Hospital B, and HIE

Move the clinical and interoperability modules into their own services.

Hospital A service:

- native consult persistence
- Hospital A FHIR mapping
- patient push receive/store logic

Hospital B service:

- reuse the existing Mongo entities and repositories
- keep `hospital_b` as the native Hospital B database
- preserve latest-consult lookup by ABHA ID

HIE/FHIR Exchange service:

- consent-aware pull/push orchestration
- HAPI FHIR validation
- hospital-to-hospital exchange coordination
- cross-hospital doctor/patient lookup endpoints

Important rule:
The HIE service should orchestrate across services and should not directly own Hospital A or Hospital B native persistence.

### Phase 5: Database Separation for Platform Services

Right now, shared platform tables still live in one PostgreSQL database. For the final microservices architecture, separate ownership cleanly.

Target split:

- Auth/Identity -> `auth_identity_db`
- Consent -> `consent_db`
- Notification/Audit -> `notification_audit_db`

Practical implementation approach:

- first split services logically
- then move each service to its own database/schema
- keep Hospital A on PostgreSQL and Hospital B on MongoDB throughout

### Phase 6: Docker and Local Orchestration

Once the services exist, add containerization and a reproducible local stack.

Deliverables:

- one Dockerfile per backend service
- one Dockerfile per frontend
- root `.env.example`
- root `docker-compose.yml`

Compose stack should include:

- all backend services
- all three frontends
- Eureka
- API Gateway
- PostgreSQL instances
- MongoDB
- optional pgAdmin
- optional mongo-express
- ELK stack

### Phase 7: Jenkins CI/CD

Add Jenkins-based CI/CD to match the SPE report style.

Pipeline goals:

- webhook trigger from GitHub
- detect changed components
- run tests only where needed
- build JARs and frontend bundles
- build Docker images
- tag images with commit SHA
- push to registry
- deploy updated workloads to Kubernetes
- wait for rollout status

### Phase 8: Kubernetes, Ingress, and Secrets

Deploy the microservices stack into Kubernetes once Docker and Jenkins are in place.

Required pieces:

- namespace for app workloads
- namespace for observability
- Deployments for stateless services and frontends
- StatefulSets for PostgreSQL, MongoDB, and Elasticsearch
- ClusterIP services for internal networking
- NGINX Ingress for public access
- ConfigMaps for non-secret config
- Secrets for DB credentials, JWT values, and internal service tokens

### Phase 9: ELK Logging and Operational Verification

Add centralized operational visibility across the microservices environment.

Deliverables:

- structured JSON backend logs
- Filebeat
- Logstash
- Elasticsearch
- Kibana dashboards

Dashboards should cover:

- authentication failures
- consent approvals and revocations
- HIE exchange attempts
- FHIR validation failures
- Hospital B Mongo persistence events
- service-level errors and latency indicators

### Phase 10: Final Hardening and Demo Readiness

Before final presentation or evaluation, close the deployment loop.

Deliverables:

- readiness and liveness probes
- NetworkPolicies
- smoke tests after deployment
- backup and restore notes for PostgreSQL, MongoDB, and Elasticsearch
- final deployment runbook for the demo

Recommended smoke tests:

- login works
- consent request and approval works
- Hospital A native consult save works
- HIE exchange works
- Hospital B stores received data in MongoDB
- audit trail is visible
- logs are visible in Kibana

## Updated Implementation Roadmap

1. Treat the current codebase as the new baseline, not as pre-migration work.
2. Freeze current API contracts and extract a shared common library.
3. Create service skeletons and Gateway/Discovery infrastructure.
4. Extract Auth/Identity, Consent, and Notification/Audit first.
5. Extract Hospital A, Hospital B, and HIE/FHIR Exchange next.
6. Split shared platform PostgreSQL ownership into service-specific databases.
7. Add Dockerfiles and `docker-compose.yml`.
8. Add Jenkins pipelines.
9. Add Kubernetes manifests and Ingress.
10. Add ELK logging and operational dashboards.
11. Add smoke tests, rollout verification, and backup notes.

## Acceptance Criteria

- all project-visible branding stays `health-bridge`
- Hospital B remains on MongoDB in the final architecture
- ABHA remains the canonical patient identity across services
- service boundaries are enforced through separate deployable apps
- the Gateway preserves the current frontend-facing route structure
- Docker Compose can start the complete local microservices stack
- Jenkins can test, build, tag, and deploy changed services
- Kubernetes deployment works through manifests and Ingress
- ELK receives and visualizes logs from all backend services
- Hospital A to Hospital B interoperability still works through FHIR and consent
- tests do not depend on a developer machine's local PostgreSQL databases

## Recommended Immediate Next Step

The best next implementation phase from the current state is Phase 1 plus Phase 2:

- freeze API contracts
- create the multi-module microservice skeleton
- keep Hospital B's existing Mongo layer intact while the rest of the backend is being split

That gives the project a real microservices starting point without redoing work that is already complete.
