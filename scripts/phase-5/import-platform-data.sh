#!/usr/bin/env bash
set -euo pipefail

IN_DIR="${IN_DIR:-./tmp/phase-5-platform-export}"
AUTH_IDENTITY_DB_URL="${AUTH_IDENTITY_DB_URL:-postgresql://fhir_user:fhir_pass@localhost:5432/auth_identity_db}"
CONSENT_DB_URL="${CONSENT_DB_URL:-postgresql://fhir_user:fhir_pass@localhost:5432/consent_db}"
NOTIFICATION_AUDIT_DB_URL="${NOTIFICATION_AUDIT_DB_URL:-postgresql://fhir_user:fhir_pass@localhost:5432/notification_audit_db}"

psql "$AUTH_IDENTITY_DB_URL" -f "$IN_DIR/auth_identity_data.sql"
psql "$CONSENT_DB_URL" -f "$IN_DIR/consent_data.sql"
psql "$NOTIFICATION_AUDIT_DB_URL" -f "$IN_DIR/notification_audit_data.sql"

printf 'Imported Phase 5 platform data from %s\n' "$IN_DIR"
