#!/usr/bin/env bash
set -euo pipefail

SOURCE_DB_URL="${SOURCE_DB_URL:-postgresql://fhir_user:fhir_pass@localhost:5432/fhir_main}"
OUT_DIR="${OUT_DIR:-./tmp/phase-5-platform-export}"

mkdir -p "$OUT_DIR"

pg_dump "$SOURCE_DB_URL" \
  --data-only \
  --column-inserts \
  --table=auth_users \
  --table=patients \
  --table=hospital_patient_links \
  --table=hospitals \
  > "$OUT_DIR/auth_identity_data.sql"

pg_dump "$SOURCE_DB_URL" \
  --data-only \
  --column-inserts \
  --table=consent_requests \
  --table=consent_audit_log \
  > "$OUT_DIR/consent_data.sql"

pg_dump "$SOURCE_DB_URL" \
  --data-only \
  --column-inserts \
  --table=ehr_exchange_log \
  --table=patient_push_notifications \
  > "$OUT_DIR/notification_audit_data.sql"

printf 'Exported Phase 5 platform data to %s\n' "$OUT_DIR"
