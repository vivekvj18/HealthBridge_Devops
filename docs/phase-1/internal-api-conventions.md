# Internal API Conventions

Phase 1 contract for future service-to-service APIs.

## Route prefix

- Reserve `/internal/**` for service-to-service routes.
- Frontends must never call `/internal/**` directly.
- The future Gateway should not expose `/internal/**` to public clients.

## Authentication baseline

Until the services adopt signed internal JWTs, use a shared service token:

- header: `X-Internal-Service-Token`
- configured by: `app.internal.service-token`

Optional supporting headers:

- `X-Request-Id`: correlation ID propagated across services
- `X-Service-Name`: caller identity such as `hie-fhir-exchange-service`

## Payload rules

- JSON only for request/response payloads unless the route explicitly transfers raw FHIR JSON.
- Use ABHA as the canonical cross-service patient identifier.
- Use hospital codes such as `HOSP-A` and `HOSP-B` instead of display names inside internal payloads.
- Return machine-readable error payloads once the shared error model is extracted into `health-bridge-common`.

## Ownership rules

- No service should read another service's database directly.
- HIE calls Consent and Hospital services through internal APIs instead of repositories.
- Admin Reporting should aggregate through internal APIs instead of reusing another service's entity manager.
- Notification/Audit should expose append and lookup operations through internal routes, not through shared database tables.

## Recommended first internal route families

| Future service | Candidate internal route | Purpose |
| --- | --- | --- |
| Auth/Identity | `GET /internal/patients/{abhaId}` | resolve patient profile by ABHA |
| Auth/Identity | `GET /internal/hospitals/{hospitalId}` | resolve hospital metadata by code |
| Consent | `POST /internal/consents/validate` | validate active consent for a requester and patient |
| Consent | `GET /internal/consents/latest/{patientId}` | fetch latest consent snapshot |
| Hospital A | `POST /internal/op-consult/receive-fhir` | store inbound Hospital A data from HIE |
| Hospital B | `POST /internal/op-consult/receive-fhir` | store inbound Hospital B data from HIE |
| Notification/Audit | `POST /internal/notifications/patient-push` | create doctor-facing push notification |
| Notification/Audit | `POST /internal/audit/exchanges` | append exchange audit event |

## Non-goals for Phase 1

- No service-to-service route implementation yet
- No Gateway route forwarding yet
- No Eureka registration yet

This document exists so the split starts from one consistent convention instead of inventing internal routes ad hoc during Phase 2 and Phase 3.
