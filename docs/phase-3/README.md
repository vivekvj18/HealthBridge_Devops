# Phase 3: Platform Service Extraction

Phase 3 starts moving shared platform capabilities out of the monolith while preserving the Phase 1 public API shapes through the Gateway.

## Extracted services

### Auth/Identity service

Module: `services/auth-identity-service`

Owns:

- `/auth/**`
- `/auth/register/patient/**`
- `/identity/**`
- `/hospitals/**`
- `/admin/users/**`

Default database:

- PostgreSQL `auth_identity_db`

Environment overrides:

- `AUTH_IDENTITY_DB_URL`
- `AUTH_IDENTITY_DB_USERNAME`
- `AUTH_IDENTITY_DB_PASSWORD`

### Consent service

Module: `services/consent-service`

Owns:

- `/consent/**`

Default database:

- PostgreSQL `consent_db`

Environment overrides:

- `CONSENT_DB_URL`
- `CONSENT_DB_USERNAME`
- `CONSENT_DB_PASSWORD`

### Notification/Audit service

Module: `services/notification-audit-service`

Owns:

- `/patient/audit/**`
- `/admin/transfers`
- `/hospitalA/notifications/**`
- `/hospitalB/notifications/**`

Default database:

- PostgreSQL `notification_audit_db`

Environment overrides:

- `NOTIFICATION_AUDIT_DB_URL`
- `NOTIFICATION_AUDIT_DB_USERNAME`
- `NOTIFICATION_AUDIT_DB_PASSWORD`

### Admin Reporting service

Module: `services/admin-reporting-service`

Owns:

- `/admin/audit-logs`
- `/admin/system-health`

Default database:

- PostgreSQL `notification_audit_db`

Environment overrides:

- `ADMIN_REPORTING_DB_URL`
- `ADMIN_REPORTING_DB_USERNAME`
- `ADMIN_REPORTING_DB_PASSWORD`

By default, Admin Reporting reads the same audit database as Notification/Audit. It can later move to a read-only reporting store or cache.

## Current transition state

The monolith remains present as `backend` so existing local frontend testing does not break. The new extracted services are independently compiled and can be routed through the Gateway once their target PostgreSQL databases are available.

Hospital A, Hospital B, and HIE/FHIR exchange business logic are intentionally not moved in this phase. Those are Phase 4 responsibilities.

## Verification

Compile the full reactor:

```bash
./backend/mvnw -f pom.xml -DskipTests compile
```

Run all current tests:

```bash
./backend/mvnw -f pom.xml test
```
