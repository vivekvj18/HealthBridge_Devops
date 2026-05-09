-- Platform target table ownership reference.
--
-- These checks are meant to be run after the extracted services have started
-- with JPA ddl-auto=update or after equivalent DDL migrations have been applied.

\connect auth_identity_db
\dt auth_users
\dt patients
\dt hospital_patient_links
\dt hospitals

\connect consent_db
\dt consent_requests
\dt consent_audit_log

\connect notification_audit_db
\dt ehr_exchange_log
\dt patient_push_notifications
