# Service Boundary Mapping

Package-level mapping from the current monolith to the planned microservices split.

## Current backend package ownership

| Current package | Current responsibility | Future service |
| --- | --- | --- |
| `com.fhir.auth` | login, refresh, user creation, doctor lookup, patient registration entrypoints | Auth/Identity |
| `com.fhir.identity` | ABHA-first patient identity, patient profile, hospital patient links | Auth/Identity |
| `com.fhir.shared.hospital` | hospital registry CRUD | Auth/Identity |
| `com.fhir.consent` | consent request lifecycle and token metadata | Consent |
| `com.fhir.hospitalA` | Hospital A native consults and FHIR mapping | Hospital A |
| `com.fhir.hospitalB` | Hospital B native consults and Mongo persistence | Hospital B |
| `com.fhir.hie` | cross-hospital exchange orchestration and consent-aware pull flows | HIE/FHIR Exchange |
| `com.fhir.doctor` | doctor-driven patient discovery and link flows | HIE/FHIR Exchange |
| `com.fhir.patient` | patient consultation summaries across hospitals | HIE/FHIR Exchange |
| `com.fhir.notification` | patient push notification persistence model | Notification/Audit |
| `com.fhir.shared.audit` | transfer audit logs and patient audit views | Notification/Audit |
| `com.fhir.admin` | admin dashboards and operational summaries | Admin Reporting |

## Shared code candidates for `libs/health-bridge-common`

| Current package/class area | Why it is a shared candidate | Keep out of the shared lib |
| --- | --- | --- |
| `com.fhir.shared.security.JwtUtil` | token parsing/generation helpers will be reused by multiple services | service-specific `SecurityConfig` beans |
| `com.fhir.shared.security.SecurityContextHelper` | useful for consistent auth-context extraction | web-layer authorization rules |
| `com.fhir.shared.exception` | stable shared error models and response envelopes belong here | controller-specific exception translation until contracts are stable |
| `com.fhir.shared.validation` | common FHIR setup and validation helpers | hospital-specific mapping logic |
| cross-service DTOs from `auth`, `consent`, `hie` | useful when services begin calling each other | persistence-shaped DTOs tied to one database model |

## Phase 1 extraction order

1. Keep Hospital B exactly on MongoDB and do not redesign it during the split.
2. Extract Auth/Identity first because ABHA, login, patient profile, and hospital registry are shared dependencies.
3. Extract Consent next because HIE depends on it and its boundaries are already fairly clear.
4. Extract Notification/Audit after that because admin views and patient push flows depend on it.
5. Extract Hospital A, Hospital B, and HIE once the platform services are stable.

## Current database ownership before service split

| Database | Current owner in the monolith | Future owner |
| --- | --- | --- |
| `fhir_main` | auth, identity, consent, hospital registry, audit, notifications | split into Auth/Identity, Consent, and Notification/Audit databases |
| `hospital_a_db` | Hospital A package | Hospital A service |
| `hospital_b` | Hospital B package | Hospital B service |

## Rules for the split

- Preserve the current frontend-facing route surface behind the future API Gateway.
- Do not move repositories or persistence entities into the shared library.
- Do not let HIE own Hospital A or Hospital B native persistence.
- Do not collapse ABHA back into a local patient ID.
