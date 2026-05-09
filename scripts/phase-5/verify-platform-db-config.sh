#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

check_property() {
  local file="$1"
  local expected="$2"

  if ! grep -q "$expected" "$file"; then
    printf 'Missing expected database marker "%s" in %s\n' "$expected" "$file" >&2
    return 1
  fi
}

check_property "$ROOT_DIR/services/auth-identity-service/src/main/resources/application.properties" "auth_identity_db"
check_property "$ROOT_DIR/services/consent-service/src/main/resources/application.properties" "consent_db"
check_property "$ROOT_DIR/services/notification-audit-service/src/main/resources/application.properties" "notification_audit_db"
check_property "$ROOT_DIR/services/admin-reporting-service/src/main/resources/application.properties" "notification_audit_db"

printf 'Phase 5 platform database configuration looks separated.\n'
