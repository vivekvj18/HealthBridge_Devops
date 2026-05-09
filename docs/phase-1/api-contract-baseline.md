# API Contract Baseline

Snapshot of the current backend HTTP surface before the microservice split.

## Public and operational endpoints

| Method | Path | Request shape | Response shape | Auth today | Future owner |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/auth/register` | `RegisterRequest` | `Map<String, String>` | public | Auth/Identity |
| `POST` | `/auth/login` | `LoginRequest` | `LoginResponse` | public | Auth/Identity |
| `POST` | `/auth/refresh` | `RefreshRequest` | `Map<String, String>` | public | Auth/Identity |
| `GET` | `/auth/doctors?hospitalId=...` | query param | `List<Map<String, String>>` | public | Auth/Identity |
| `POST` | `/auth/register/patient` | `RegisterRequest` | `Map<String, Object>` | public | Auth/Identity |
| `GET` | `/auth/register/patient/{abhaId}` | path variable | `Map<String, Object>` | admin/doctor | Auth/Identity |
| `GET` | `/hospitals` | none | `List<Hospital>` | public | Auth/Identity |
| `GET` | `/actuator/health` | none | Actuator health JSON | public | platform |
| `GET` | `/actuator/health/liveness` | none | Actuator health JSON | public | platform |
| `GET` | `/actuator/health/readiness` | none | Actuator health JSON | public | platform |
| `GET` | `/actuator/info` | none | Actuator info JSON | public | platform |

## Admin endpoints

| Method | Path | Request shape | Response shape | Auth today | Future owner |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/admin/transfers` | none | `List<TransferAuditLog>` | admin | Notification/Audit |
| `GET` | `/admin/audit-logs` | none | `List<Map<String, Object>>` | admin | Admin Reporting |
| `GET` | `/admin/system-health` | none | `List<Map<String, Object>>` | admin | Admin Reporting |
| `GET` | `/admin/users` | none | `List<AppUser>` | admin | Auth/Identity |
| `POST` | `/admin/users` | `RegisterRequest` | `AppUser` | admin | Auth/Identity |
| `DELETE` | `/admin/users/{id}` | path variable | empty body | admin | Auth/Identity |
| `POST` | `/hospitals` | `Hospital` | `Hospital` | admin | Auth/Identity |
| `PUT` | `/hospitals/{id}` | `Hospital` | `Hospital` | admin | Auth/Identity |

## Consent endpoints

| Method | Path | Request shape | Response shape | Auth today | Future owner |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/consent/initiate` | `InitiateConsentDTO` | `ConsentRequestViewDTO` | admin/patient | Consent |
| `GET` | `/consent/pending/{patientId}` | path variable | `List<ConsentRequestViewDTO>` | admin/patient | Consent |
| `POST` | `/consent/respond/{requestId}` | `ConsentDecisionDTO` | `ConsentRequestViewDTO` | admin/patient | Consent |
| `POST` | `/consent/revoke/{requestId}` | none | `String` | admin/patient | Consent |

## HIE and patient lookup endpoints

| Method | Path | Request shape | Response shape | Auth today | Future owner |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/hie/exchange` | `ExchangeRequestDTO` | `ExchangeResponseDTO` | admin/doctor | HIE/FHIR Exchange |
| `POST` | `/hie/consent-only` | `ExchangeRequestDTO` | `ExchangeResponseDTO` | admin/doctor | HIE/FHIR Exchange |
| `POST` | `/hie/pull-only` | `ExchangeRequestDTO` | `ExchangeResponseDTO` | admin/doctor | HIE/FHIR Exchange |
| `GET` | `/hie/exchange/status/{consentId}` | path variable | `ExchangeResponseDTO` | admin/doctor | HIE/FHIR Exchange |
| `GET` | `/doctor/patients/lookup/{identifier}` | path variable | `DoctorPatientLookupResponseDTO` | doctor | HIE/FHIR Exchange |
| `POST` | `/doctor/patients/link/{abhaId}` | path variable | `Map<String, String>` | doctor | HIE/FHIR Exchange |
| `GET` | `/patient/consultations/{abhaId}` | path variable | `List<PatientConsultationSummaryDTO>` | authenticated fallback | HIE/FHIR Exchange |
| `GET` | `/patient/audit/{patientId}` | path variable | `List<TransferAuditLog>` | authenticated fallback | Notification/Audit |

## Identity endpoints

| Method | Path | Request shape | Response shape | Auth today | Future owner |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/identity/register` | `RegisterPatientDTO` | `PatientProfile` | admin | Auth/Identity |

## Hospital A endpoints

| Method | Path | Request shape | Response shape | Auth today | Future owner |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/hospitalA/op-consult` | `HospitalAOPConsultRecordDTO` | `String` | admin/doctor | Hospital A |
| `POST` | `/hospitalA/op-consult/receive` | raw FHIR JSON | `HospitalAOPConsultRecordDTO` | admin/doctor | Hospital A |
| `POST` | `/hospitalA/op-consult/push` | `PatientPushRequestDTO` | `String` | patient | Hospital A |
| `GET` | `/hospitalA/notifications` | none | `List<PatientPushNotification>` | admin/doctor | Notification/Audit |
| `PATCH` | `/hospitalA/notifications/{id}/read` | path variable | `PatientPushNotification` | admin/doctor | Notification/Audit |

## Hospital B endpoints

| Method | Path | Request shape | Response shape | Auth today | Future owner |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/hospitalB/op-consult` | raw FHIR JSON | `HospitalBOPConsultRecordDTO` | admin/doctor | Hospital B |
| `POST` | `/hospitalB/op-consult/native` | `HospitalBOPConsultRecordDTO` | `String` | admin/doctor | Hospital B |
| `GET` | `/hospitalB/op-consult` | none | `List<HospitalBOPConsultEntity>` | admin/doctor | Hospital B |
| `POST` | `/hospitalB/op-consult/push` | `PatientPushRequestBDTO` | `String` | patient | Hospital B |
| `GET` | `/hospitalB/notifications` | none | `List<PatientPushNotification>` | admin/doctor | Notification/Audit |
| `PATCH` | `/hospitalB/notifications/{id}/read` | path variable | `PatientPushNotification` | admin/doctor | Notification/Audit |

## Notes

- This baseline captures the route ownership before any Gateway is introduced.
- Future service extraction should preserve these public route shapes through the Gateway, even if the backing service changes.
- `/patient/consultations/**` and `/patient/audit/**` currently rely on the authenticated fallback rule and should receive explicit policy when the Gateway is introduced.
