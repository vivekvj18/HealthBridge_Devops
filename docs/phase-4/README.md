# Phase 4: Clinical and HIE Extraction

Phase 4 starts extracting the clinical hospital modules and HIE/FHIR exchange orchestration into their target service modules while keeping the existing `backend` monolith intact for transition.

## Extracted services

### Hospital A service

Module: `services/hospital-a-service`

Owns:

- `/hospitalA/op-consult/**`
- Hospital A native OP consult persistence
- Hospital A patient-local record links
- Hospital A FHIR bundle mapping
- Hospital A FHIR receive and pull support

Default database:

- PostgreSQL `hospital_a_db`

Environment overrides:

- `HOSPITAL_A_DB_URL`
- `HOSPITAL_A_DB_USERNAME`
- `HOSPITAL_A_DB_PASSWORD`

### Hospital B service

Module: `services/hospital-b-service`

Owns:

- `/hospitalB/op-consult/**`
- Hospital B native OP consult persistence
- Hospital B patient-local record links
- Hospital B FHIR bundle mapping
- Hospital B FHIR receive and pull support

Default native database:

- MongoDB `hospital_b`

Environment overrides:

- `HOSPITAL_B_MONGO_URI`
- `HOSPITAL_B_MONGO_DATABASE`
- `HOSPITAL_B_MONGO_AUTO_INDEX_CREATION`

### HIE/FHIR Exchange service

Module: `services/hie-fhir-exchange-service`

Owns:

- `/hie/**`
- `/doctor/patients/**`
- `/patient/consultations/**`
- consent-aware pull orchestration
- cross-hospital doctor/patient lookup flow
- patient consultation aggregation flow
- FHIR validation support

## Current transition state

The monolith-era code used direct in-JVM calls between HIE, hospital, consent, identity, audit, and notification classes. For this phase, the extracted services compile independently with those dependencies copied into the target modules so the application can keep moving without breaking the current backend.

The next hardening step should replace this transition coupling with real service-to-service clients:

- HIE should call Auth/Identity for patient identity and link operations.
- HIE should call Consent for consent lifecycle and token checks.
- HIE should call Notification/Audit for audit and patient notification writes.
- HIE should call Hospital A and Hospital B over internal APIs instead of reading their repositories directly.
- Hospital A and Hospital B patient-push flows should call Consent and Notification/Audit over internal APIs.

## Verification

Compile the full reactor:

```bash
./backend/mvnw -f pom.xml -DskipTests compile
```

Run all current tests:

```bash
./backend/mvnw -f pom.xml test
```
