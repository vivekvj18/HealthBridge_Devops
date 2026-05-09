-- Platform database split.
--
-- Run this with psql as a PostgreSQL role that can create databases.
-- This file intentionally uses psql's \gexec so it can be rerun safely.

SELECT 'CREATE DATABASE auth_identity_db'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'auth_identity_db'
)\gexec

SELECT 'CREATE DATABASE consent_db'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'consent_db'
)\gexec

SELECT 'CREATE DATABASE notification_audit_db'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'notification_audit_db'
)\gexec
