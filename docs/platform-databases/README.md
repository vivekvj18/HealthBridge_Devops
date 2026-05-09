# Platform Database Separation

This document defines database-per-service ownership for shared platform data.

## Target databases

| Service | Database | Owned tables |
| --- | --- | --- |
| Auth/Identity | `auth_identity_db` | `auth_users`, `patients`, `hospital_patient_links`, `hospitals` |
| Consent | `consent_db` | `consent_requests`, `consent_audit_log` |
| Notification/Audit | `notification_audit_db` | `ehr_exchange_log`, `patient_push_notifications` |
| Admin Reporting | `notification_audit_db` read side for now | reads exchange audit data |

Hospital A stays on PostgreSQL `hospital_a_db`, and Hospital B stays on MongoDB `hospital_b`.

## Service configuration

The extracted platform services already point to the separated databases:

- `AUTH_IDENTITY_DB_URL`
- `CONSENT_DB_URL`
- `NOTIFICATION_AUDIT_DB_URL`
- `ADMIN_REPORTING_DB_URL`

The default local values are stored in each service's `application.properties`.

## Create databases

Run:

```bash
psql postgres -f db/create-platform-databases.sql
```

The script is rerunnable and creates:

- `auth_identity_db`
- `consent_db`
- `notification_audit_db`

## Fresh application startup

This project is treated as a fresh microservices application. Do not migrate data from the old monolith `fhir_main` database.

After creating the empty databases, start the extracted services and let Hibernate create/update the service-owned tables with `JPA_DDL_AUTO=update` for local development. Production deployments should replace this with explicit migration tooling.

## Verify

Check config markers:

```bash
scripts/verify-platform-db-config.sh
```

Check tables after services have started:

```bash
psql postgres -f db/platform-table-ownership.sql
```

Compile and test:

```bash
./backend/mvnw -f pom.xml -DskipTests compile
./backend/mvnw -f pom.xml test
```

## Transition note

The monolith still exists as a compatibility fallback. During the next hardening pass, copied transition dependencies in HIE and hospital services should be replaced with internal HTTP clients so no service reads another service's database directly.
