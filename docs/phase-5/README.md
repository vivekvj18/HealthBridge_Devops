# Phase 5: Platform Database Separation

Phase 5 formalizes the database-per-service ownership for shared platform data.

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
psql postgres -f db/phase-5/create-platform-databases.sql
```

The script is rerunnable and creates:

- `auth_identity_db`
- `consent_db`
- `notification_audit_db`

## Move existing local data

If a developer already has data in the old monolith `fhir_main` database, export it first:

```bash
scripts/phase-5/export-platform-data.sh
```

Start each extracted platform service once so Hibernate can create/update its target tables, then import:

```bash
scripts/phase-5/import-platform-data.sh
```

The scripts use these defaults:

- source: `postgresql://fhir_user:fhir_pass@localhost:5432/fhir_main`
- auth target: `postgresql://fhir_user:fhir_pass@localhost:5432/auth_identity_db`
- consent target: `postgresql://fhir_user:fhir_pass@localhost:5432/consent_db`
- notification/audit target: `postgresql://fhir_user:fhir_pass@localhost:5432/notification_audit_db`

Override the corresponding environment variables when needed.

## Verify

Check config markers:

```bash
scripts/phase-5/verify-platform-db-config.sh
```

Check tables after services have started:

```bash
psql postgres -f db/phase-5/platform-table-ownership.sql
```

Compile and test:

```bash
./backend/mvnw -f pom.xml -DskipTests compile
./backend/mvnw -f pom.xml test
```

## Transition note

The monolith still exists as a compatibility fallback. During the next hardening pass, copied transition dependencies in HIE and hospital services should be replaced with internal HTTP clients so no service reads another service's database directly.
