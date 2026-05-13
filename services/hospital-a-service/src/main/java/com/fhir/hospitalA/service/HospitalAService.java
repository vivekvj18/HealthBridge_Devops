package com.fhir.hospitalA.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fhir.consent.service.ConsentStore;
import com.fhir.hospitalA.dto.HospitalAOPConsultRecordDTO;
import com.fhir.hospitalA.dto.PatientPushRequestDTO;
import com.fhir.hospitalA.mapper.FhirBundleToHospitalAMapper;
import com.fhir.hospitalA.mapper.HospitalAOPConsultToFhirMapper;
import com.fhir.hospitalA.model.HospitalAOPConsultEntity;
import com.fhir.hospitalA.model.HospitalAPatient;
import com.fhir.hospitalA.repository.HospitalAOPConsultRepository;
import com.fhir.hospitalA.repository.HospitalAPatientRepository;
import com.fhir.notification.PatientPushNotification;
import com.fhir.notification.PatientPushNotificationRepository;
import com.fhir.shared.audit.AuditService;
import com.fhir.shared.security.JwtUtil;
import com.fhir.shared.validation.FHIRValidatorBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Service layer for Hospital A.
 * <p>
 * Owns all business logic: entity persistence, consent gate, FHIR bundle
 * assembly, granular privacy filtering, and audit logging.  Controllers
 * are kept as thin HTTP adapters that only delegate here.
 */
@Service
public class HospitalAService {

    private final FhirContext fhirContext = FhirContext.forR4();

    @Autowired
    private FHIRValidatorBundle bundleValidator;

    @Autowired
    private ConsentStore consentStore;

    @Autowired
    private HospitalAPatientRepository patientRepository;

    @Autowired
    private HospitalAOPConsultRepository consultRepository;

    @Autowired
    private PatientPushNotificationRepository pushNotificationRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private JwtUtil jwtUtil;

    // ── Doctor-Initiated OP Consult ──────────────────────────────────────────

    /**
     * Handles the full doctor-initiated OP Consult pipeline:
     * <ol>
     *   <li>Persist the visit record locally.</li>
     *   <li>Resolve the ABHA-ID from the local patient record when needed.</li>
     *   <li>Build and validate the local FHIR bundle.</li>
     * </ol>
     *
     * @param consultRecord the inbound OP consult DTO
     * @param requesterId   the authenticated requester identity (from JWT subject)
     * @return success message for local consult storage
     */
    @Transactional
    public String processOPConsult(HospitalAOPConsultRecordDTO consultRecord, String requesterId) {
        resolveConsultPatientIdentity(consultRecord);

        // 1. Persist the OPD visit locally immediately
        persistOPConsult(consultRecord);

        // 2. Build and validate the local FHIR bundle. Sharing is consent-gated
        // later by the HIE pull/push flows, not by local consult submission.
        Bundle bundle = HospitalAOPConsultToFhirMapper.mapToBundle(consultRecord);
        bundleValidator.validate(bundle);
        return "OP Consult record stored in Hospital A database successfully.";
    }

    @Transactional
    public HospitalAOPConsultRecordDTO receiveFhirBundle(String fhirJson) {
        Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, fhirJson);

        // Validate BEFORE mapping — reject invalid FHIR bundles (HTTP 422) immediately
        bundleValidator.validate(bundle);

        HospitalAOPConsultRecordDTO dto = FhirBundleToHospitalAMapper.map(bundle);

        // Persist with provenance: mark this row as received from Hospital B via FHIR
        HospitalAOPConsultEntity entity = new HospitalAOPConsultEntity();
        entity.setPatientId(dto.getPatientId());
        entity.setAbhaId(dto.getAbhaId());
        entity.setPatientFirstName(dto.getPatientFirstName());
        entity.setPatientLastName(dto.getPatientLastName());
        entity.setDoctorName(dto.getDoctorName());
        entity.setVisitDate(dto.getVisitDate());
        entity.setSymptoms(dto.getSymptoms());
        entity.setTemperature(dto.getTemperature());
        entity.setBloodPressure(dto.getBloodPressure());
        entity.setPrescriptionPdfBase64(dto.getPrescriptionPdfBase64());
        // Provenance fields
        entity.setReceivedViaFhir(true);
        entity.setSourceHospital("HOSP-B");
        entity.setSourceRecordId(null); // source record id is only set when provided by the source system
        consultRepository.save(entity);

        return dto;
    }

    @Transactional(readOnly = true)
    public List<HospitalAOPConsultEntity> getConsultsByAbhaId(String abhaId) {
        return consultRepository.findByAbhaIdOrderByCreatedAtDesc(abhaId);
    }

    // ── Patient-Initiated Push ───────────────────────────────────────────────

    /**
     * Handles the patient-initiated push pipeline:
     * <ol>
     *   <li>Fetch the latest consult record for the authenticated patient.</li>
     *   <li>Auto-grant consent for the requested data types.</li>
     *   <li>Build &amp; filter the FHIR bundle.</li>
     *   <li>Log a pending audit entry, validate, encode, mark success/failure.</li>
     * </ol>
     *
     * @param pushRequest inbound push request DTO (target requester + data types)
     * @param patientId   the authenticated patient's ID (extracted from JWT by the controller)
     * @return pretty-printed FHIR Bundle JSON
     */
    @Transactional
    public String pushOPConsult(PatientPushRequestDTO pushRequest, String patientId) {

        // 1. Fetch the latest consult record for this patient
        HospitalAOPConsultEntity latestConsult = consultRepository
                .findFirstByAbhaIdOrderByIdDesc(patientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No recent OP consult record found for patient: " + patientId));

        // 2. Auto-grant and record consent for this patient-initiated transfer
        consentStore.autoGrantForPatientPush(
                patientId,
                pushRequest.getTargetRequesterId(),
                pushRequest.getDataTypes());

        // 3. Map entity → DTO → FHIR bundle
        HospitalAOPConsultRecordDTO dto = entityToDTO(latestConsult);
        Bundle bundle = HospitalAOPConsultToFhirMapper.mapToBundle(dto);

        // 4. Apply fine-grained privacy stripping based on patient-selected data types
        filterBundleByConsent(bundle, pushRequest.getDataTypes());

        // 5. Audit → validate → encode
        String dataTypesStr = pushRequest.getDataTypes() != null
                ? pushRequest.getDataTypes().toString() : "[]";

        Long auditId = auditService.logPending(
                patientId,
                "HospitalA",
                pushRequest.getTargetRequesterId(),
                bundle.getEntry().size(),
                dataTypesStr
        );

        try {
            bundleValidator.validate(bundle);
            IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
            String payload = parser.encodeResourceToString(bundle);
            auditService.markSuccess(auditId);

            // Save a push notification so the target doctor sees it in their dashboard.
            // Use the hospital the patient chose; fall back to HOSP-A for backward-compat.
            String targetHospital = (pushRequest.getTargetHospitalId() != null
                    && !pushRequest.getTargetHospitalId().isBlank())
                    ? pushRequest.getTargetHospitalId()
                    : "HOSP-A";

            PatientPushNotification notification = new PatientPushNotification();
            notification.setPatientAbhaId(patientId);
            notification.setPatientName(
                    latestConsult.getPatientFirstName() + " " + latestConsult.getPatientLastName());
            notification.setTargetDoctorUsername(pushRequest.getTargetRequesterId());
            notification.setHospitalCode(targetHospital);
            notification.setSourceHospitalId("HOSP-A");
            notification.setTargetHospitalId(targetHospital);
            notification.setDataTypes(
                    pushRequest.getDataTypes() != null
                            ? String.join(",", pushRequest.getDataTypes()) : "");
            notification.setFhirBundleJson(payload);
            notification.setFhirBundleHash(auditService.hashPayload(payload));
            notification.setRead(false);
            if ("HOSP-B".equals(targetHospital)) {
                // Call Hospital B to deliver the notification
                try {
                    System.out.println(">>> Sending notification to Hospital B for doctor: " + notification.getTargetDoctorUsername());
                    org.springframework.web.client.RestClient.create("http://hospital-b-service:9098")
                        .post()
                        .uri("/hospitalB/notifications/receive")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(notification)
                        .retrieve()
                        .toBodilessEntity();
                } catch (Exception e) {
                    System.err.println("Failed to push to Hospital B: " + e.getMessage());
                }
            } else {
                pushNotificationRepository.save(notification);
            }

            return payload;
        } catch (Exception e) {
            auditService.markFailed(auditId, e.getMessage());
            throw e;
        }
    }

    // ── Notification Support ─────────────────────────────────────────────────

    /**
     * Returns all push notifications sent to a specific doctor at Hospital A,
     * newest first.
     */
    @Transactional(readOnly = true)
    public List<PatientPushNotification> getNotificationsForDoctor(String doctorUsername) {
        return pushNotificationRepository
                .findByTargetDoctorUsernameAndHospitalCodeOrderByPushedAtDesc(
                        doctorUsername, "HOSP-A");
    }

    /**
     * Marks a single notification as read. Returns the updated entity.
     * Throws 404 if not found, 403 if the notification does not belong to this doctor.
     */
    @Transactional
    public PatientPushNotification markNotificationRead(Long notificationId, String doctorUsername) {
        PatientPushNotification notification = pushNotificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + notificationId));
        if (!notification.getTargetDoctorUsername().equals(doctorUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Notification does not belong to the authenticated doctor.");
        }
        notification.setRead(true);
        notification.setReadAt(java.time.Instant.now());
        return pushNotificationRepository.save(notification);
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Maps a DTO to a new {@link HospitalAOPConsultEntity} and saves it.
     */
    private void persistOPConsult(HospitalAOPConsultRecordDTO dto) {
        HospitalAOPConsultEntity entity = new HospitalAOPConsultEntity();
        entity.setPatientId(dto.getPatientId());
        entity.setAbhaId(dto.getAbhaId());
        entity.setPatientFirstName(dto.getPatientFirstName());
        entity.setPatientLastName(dto.getPatientLastName());
        entity.setDoctorName(dto.getDoctorName());
        entity.setVisitDate(dto.getVisitDate());
        entity.setSymptoms(dto.getSymptoms());
        entity.setTemperature(dto.getTemperature());
        entity.setBloodPressure(dto.getBloodPressure());
        entity.setPrescriptionPdfBase64(dto.getPrescriptionPdfBase64());
        consultRepository.save(entity);
    }

    private void resolveConsultPatientIdentity(HospitalAOPConsultRecordDTO dto) {
        if (isBlank(dto.getAbhaId()) && !isBlank(dto.getPatientId()) && dto.getPatientId().startsWith("ABHA-")) {
            dto.setAbhaId(dto.getPatientId());
        }

        if (isBlank(dto.getAbhaId()) && !isBlank(dto.getPatientId())) {
            patientRepository.findById(dto.getPatientId())
                .map(HospitalAPatient::getAbhaId)
                .filter(abhaId -> !isBlank(abhaId))
                .ifPresent(dto::setAbhaId);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Maps a persisted {@link HospitalAOPConsultEntity} back to a DTO so it
     * can be converted to a FHIR bundle.
     */
    private HospitalAOPConsultRecordDTO entityToDTO(HospitalAOPConsultEntity entity) {
        HospitalAOPConsultRecordDTO dto = new HospitalAOPConsultRecordDTO();
        dto.setPatientId(entity.getPatientId());
        dto.setAbhaId(entity.getAbhaId());
        dto.setPatientFirstName(entity.getPatientFirstName());
        dto.setPatientLastName(entity.getPatientLastName());
        dto.setDoctorName(entity.getDoctorName());
        dto.setVisitDate(entity.getVisitDate());
        dto.setSymptoms(entity.getSymptoms());
        dto.setTemperature(entity.getTemperature());
        dto.setBloodPressure(entity.getBloodPressure());
        dto.setPrescriptionPdfBase64(entity.getPrescriptionPdfBase64());
        return dto;
    }

    /**
     * Removes bundle entries whose FHIR resource type is not covered by the
     * set of granted data types.
     *
     * <p>Base resources (Patient, Encounter, Practitioner, DocumentReference,
     * Consent) are always included as they form the structural summary envelope.
     * Optional clinical resources are included only when explicitly granted.
     *
     * @param bundle           the FHIR bundle to mutate in-place
     * @param grantedDataTypes the set of logical data-type grants (e.g. "Medications")
     */
    private void filterBundleByConsent(Bundle bundle, Set<String> grantedDataTypes) {
        Set<String> allowed = new HashSet<>();

        // Structural / summary types always shared
        allowed.add("Patient");
        allowed.add("Encounter");
        allowed.add("Practitioner");
        allowed.add("Consent");

        // Optional clinical types gated by consent grants
        if (grantedDataTypes != null) {
            // Legacy scope names
            if (grantedDataTypes.contains("Medications")) {
                allowed.add("Medication");
                allowed.add("MedicationRequest");
                allowed.add("MedicationStatement");
            }
            if (grantedDataTypes.contains("Diagnostics")) {
                allowed.add("DiagnosticReport");
                allowed.add("Observation");
            }
            if (grantedDataTypes.contains("LabResults")) {
                allowed.add("Observation");
            }
            if (grantedDataTypes.contains("SurgicalHistory")) {
                allowed.add("Procedure");
            }
            if (grantedDataTypes.contains("Allergies")) {
                allowed.add("AllergyIntolerance");
            }
            // New canonical scope names
            if (grantedDataTypes.contains("OP_CONSULT")) {
                allowed.add("Observation"); // vitals + symptoms
                allowed.add("DiagnosticReport");
            }
            if (grantedDataTypes.contains("PRESCRIPTION")) {
                allowed.add("Medication");
                allowed.add("MedicationRequest");
                allowed.add("MedicationStatement");
                allowed.add("DocumentReference");
            }
            if (grantedDataTypes.contains("LAB_RESULT")) {
                allowed.add("Observation");
                allowed.add("DiagnosticReport");
            }
        }

        Iterator<Bundle.BundleEntryComponent> iterator = bundle.getEntry().iterator();
        while (iterator.hasNext()) {
            Bundle.BundleEntryComponent entry = iterator.next();
            if (entry.getResource() != null) {
                String resourceType = entry.getResource().getResourceType().name();
                if (!allowed.contains(resourceType)) {
                    iterator.remove();
                }
            }
        }
    }

    // ── HIE Gateway Support ──────────────────────────────────────────────────

    /**
     * Called by HIPFhirClient when HIE requests data.
     * Validates consent JWT, fetches latest consult, applies existing
     * granular filter, returns FHIR bundle JSON.
     *
     * STUB MODE: while SKIP_JWT_VALIDATION = true, skips token verification.
     * Set to false once Person 1 delivers ConsentTokenService.
     */
    private static final boolean SKIP_JWT_VALIDATION = false; // flip to false after merge

    @Transactional(readOnly = true)
    public String pullFhirBundle(String patientId, String consentToken, Set<String> scope) {

        if (!SKIP_JWT_VALIDATION) {
            // Validate consent JWT — final trust enforcement at HIP
            io.jsonwebtoken.Claims claims;
            try {
                claims = jwtUtil.parse(consentToken);
            } catch (io.jsonwebtoken.JwtException e) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Invalid consent token: " + e.getMessage());
            }

            String tokenType = claims.get("type", String.class);
            if (!"consent".equals(tokenType)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Token is not a consent token");
            }

            String tokenPatient = claims.getSubject();
            if (!patientId.equals(tokenPatient)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Consent token patient mismatch");
            }
        }

        // Fetch latest consult record for this patient using ABHA-ID
        HospitalAOPConsultEntity consult = consultRepository
            .findFirstByAbhaIdOrderByIdDesc(patientId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "No consult record found for patient: " + patientId));

        // Use existing mapper and filter — no duplication
        HospitalAOPConsultRecordDTO dto = entityToDTO(consult);
        org.hl7.fhir.r4.model.Bundle bundle =
            HospitalAOPConsultToFhirMapper.mapToBundle(dto);
        filterBundleByConsent(bundle, scope);

        return fhirContext.newJsonParser()
            .setPrettyPrint(true)
            .encodeResourceToString(bundle);
    }
}
