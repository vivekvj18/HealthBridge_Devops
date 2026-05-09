package com.fhir.hospitalB.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fhir.hospitalB.dto.HospitalBOPConsultRecordDTO;
import com.fhir.hospitalB.dto.PatientPushRequestBDTO;
import com.fhir.hospitalB.mapper.FhirBundleToHospitalBMapper;
import com.fhir.hospitalB.mapper.HospitalBOPConsultToFhirMapper;
import com.fhir.hospitalB.model.HospitalBPatient;
import com.fhir.hospitalB.model.HospitalBOPConsultEntity;
import com.fhir.hospitalB.repository.HospitalBOPConsultRepository;
import com.fhir.hospitalB.repository.HospitalBPatientRepository;
import com.fhir.notification.PatientPushNotification;
import com.fhir.notification.PatientPushNotificationRepository;
import com.fhir.consent.service.ConsentStore;
import com.fhir.shared.audit.AuditService;
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
 * Service layer for Hospital B.
 * <p>
 * Owns all business logic: FHIR JSON parsing and domain model mapping.
 * The controller is kept as a thin HTTP adapter that only delegates here.
 */
@Service
public class HospitalBService {

    private final FhirContext fhirContext = FhirContext.forR4();

    @Autowired
    private FHIRValidatorBundle bundleValidator;

    @Autowired
    private HospitalBOPConsultRepository consultRepository;

    @Autowired
    private HospitalBPatientRepository patientRepository;

    @Autowired
    private PatientPushNotificationRepository pushNotificationRepository;

    @Autowired
    private ConsentStore consentStore;

    @Autowired
    private AuditService auditService;

    /**
     * Parses and validates a FHIR Bundle from another hospital, maps it into
     * Hospital B's local schema, and persists it with provenance metadata.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Parse FHIR JSON → {@link Bundle}</li>
     *   <li>Validate bundle against FHIR R4 base profiles (HTTP 422 on failure)</li>
     *   <li>Map bundle → Hospital B DTO (yyyy-MM-dd date, plain numeric temp)</li>
     *   <li>Persist entity with sourceHospital / receivedViaFhir provenance fields</li>
     * </ol>
     *
     * @param fhirJson raw FHIR-compliant Bundle JSON
     * @return the mapped {@link HospitalBOPConsultRecordDTO}
     */
    public HospitalBOPConsultRecordDTO receiveFhirBundle(String fhirJson) {
        IParser parser = fhirContext.newJsonParser();
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

        // Validate BEFORE mapping — reject invalid FHIR bundles (HTTP 422) immediately
        bundleValidator.validate(bundle);

        HospitalBOPConsultRecordDTO dto = FhirBundleToHospitalBMapper.map(bundle);

        // Persist to Hospital B's MongoDB store with provenance stamps
        HospitalBOPConsultEntity entity = new HospitalBOPConsultEntity();
        entity.setAbhaId(dto.getAbhaId());
        entity.setPatientId(dto.getPatientId());
        entity.setPatientName(dto.getPatientName());
        entity.setConsultDate(dto.getConsultDate());         // yyyy-MM-dd (Hospital B native)
        entity.setDoctor(dto.getDoctor());
        entity.setClinicalNotes(dto.getClinicalNotes());
        entity.setConsentVerified(dto.isConsentVerified());
        if (dto.getVitals() != null) {
            entity.setBloodPressure(dto.getVitals().getBp());
            entity.setTemperature(dto.getVitals().getTemp()); // plain decimal e.g. "40.0"
        }
        if (dto.getPrescriptionPdfBase64() != null) {
            entity.setPrescriptionPdfBase64(dto.getPrescriptionPdfBase64());
        }
        // Provenance fields — make the interoperability story explicit in the DB row
        entity.setReceivedViaFhir(true);
        entity.setSourceHospital("HOSP-A");
        entity.setSourceRecordId(null); // source record id is only set when provided by the source system
        entity.stampCreated();
        consultRepository.save(entity);

        return dto;
    }

    public String processNativeConsult(HospitalBOPConsultRecordDTO dto) {
        resolvePatientIdentity(dto);

        HospitalBOPConsultEntity entity = new HospitalBOPConsultEntity();
        entity.setAbhaId(dto.getAbhaId());
        entity.setPatientId(dto.getPatientId());
        entity.setPatientName(dto.getPatientName());
        entity.setConsultDate(dto.getConsultDate());
        entity.setDoctor(dto.getDoctor());
        entity.setClinicalNotes(dto.getClinicalNotes());
        entity.setConsentVerified(true);
        if (dto.getVitals() != null) {
            entity.setBloodPressure(dto.getVitals().getBp());
            entity.setTemperature(dto.getVitals().getTemp());
        }
        entity.setPrescriptionPdfBase64(dto.getPrescriptionPdfBase64());
        entity.setReceivedViaFhir(false);
        entity.setSourceHospital("HOSP-B");
        entity.stampCreated();
        consultRepository.save(entity);
        return "OP Consult record stored in Hospital B database successfully.";
    }

    public java.util.List<HospitalBOPConsultEntity> getAllConsults() {
        return consultRepository.findAll();
    }

    public List<HospitalBOPConsultEntity> getConsultsByAbhaId(String abhaId) {
        return consultRepository.findByAbhaIdOrderByReceivedAtDesc(abhaId);
    }

    /**
     * Called by HIPFhirClient when HIE requests data from Hospital B.
     * The assembled bundle is validated before being serialised — invalid
     * outbound FHIR never leaves Hospital B.
     */
    public String pullFhirBundle(String abhaId, String consentToken, java.util.Set<String> scope) {
        HospitalBOPConsultEntity consult = consultRepository
            .findFirstByAbhaIdOrderByReceivedAtDesc(abhaId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "No consult record found for patient with ABHA-ID: " + abhaId));

        HospitalBOPConsultRecordDTO dto = new HospitalBOPConsultRecordDTO();
        dto.setAbhaId(consult.getAbhaId());
        dto.setPatientId(consult.getPatientId());
        dto.setPatientName(consult.getPatientName());
        dto.setConsultDate(consult.getConsultDate());
        dto.setDoctor(consult.getDoctor());
        dto.setClinicalNotes(consult.getClinicalNotes());

        HospitalBOPConsultRecordDTO.Vitals vitals = new HospitalBOPConsultRecordDTO.Vitals();
        vitals.setBp(consult.getBloodPressure());
        vitals.setTemp(consult.getTemperature());
        dto.setVitals(vitals);

        dto.setPrescriptionPdfBase64(consult.getPrescriptionPdfBase64());

        Bundle bundle = com.fhir.hospitalB.mapper.HospitalBOPConsultToFhirMapper.mapToBundle(dto);
        filterBundleByConsent(bundle, scope);

        // Validate outbound bundle — never serialise invalid FHIR
        bundleValidator.validate(bundle);

        return fhirContext.newJsonParser()
            .setPrettyPrint(true)
            .encodeResourceToString(bundle);
    }

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

    private void resolvePatientIdentity(HospitalBOPConsultRecordDTO dto) {
        if (isBlank(dto.getAbhaId()) && !isBlank(dto.getPatientId()) && dto.getPatientId().startsWith("ABHA-")) {
            dto.setAbhaId(dto.getPatientId());
        }

        if (isBlank(dto.getAbhaId()) && !isBlank(dto.getPatientId())) {
            patientRepository.findByPatientId(dto.getPatientId())
                .map(HospitalBPatient::getAbhaId)
                .filter(abhaId -> !isBlank(abhaId))
                .ifPresent(dto::setAbhaId);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ── Patient-Initiated Push ───────────────────────────────────────────────

    /**
     * Patient-initiated push for Hospital B.
     * Fetches the patient's latest consult, builds a FHIR bundle,
     * and saves a push notification for the target doctor.
     */
    @Transactional
    public String pushOPConsult(PatientPushRequestBDTO pushRequest, String patientAbhaId) {
        HospitalBOPConsultEntity latestConsult = consultRepository
                .findFirstByAbhaIdOrderByReceivedAtDesc(patientAbhaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No recent OP consult record found for patient: " + patientAbhaId));

        consentStore.autoGrantForPatientPush(
                patientAbhaId,
                pushRequest.getTargetRequesterId(),
                pushRequest.getDataTypes());

        // Build DTO and FHIR bundle from latest consult
        HospitalBOPConsultRecordDTO dto = new HospitalBOPConsultRecordDTO();
        dto.setAbhaId(latestConsult.getAbhaId());
        dto.setPatientId(latestConsult.getPatientId());
        dto.setPatientName(latestConsult.getPatientName());
        dto.setConsultDate(latestConsult.getConsultDate());
        dto.setDoctor(latestConsult.getDoctor());
        dto.setClinicalNotes(latestConsult.getClinicalNotes());
        HospitalBOPConsultRecordDTO.Vitals vitals = new HospitalBOPConsultRecordDTO.Vitals();
        vitals.setBp(latestConsult.getBloodPressure());
        vitals.setTemp(latestConsult.getTemperature());
        dto.setVitals(vitals);
        dto.setPrescriptionPdfBase64(latestConsult.getPrescriptionPdfBase64());

        Bundle bundle = HospitalBOPConsultToFhirMapper.mapToBundle(dto);
        filterBundleByConsent(bundle, pushRequest.getDataTypes());
        bundleValidator.validate(bundle);

        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        String payload = parser.encodeResourceToString(bundle);

        // Save push notification for the target doctor
        PatientPushNotification notification = new PatientPushNotification();
        notification.setPatientAbhaId(patientAbhaId);
        notification.setPatientName(latestConsult.getPatientName());
        notification.setTargetDoctorUsername(pushRequest.getTargetRequesterId());
        notification.setHospitalCode("HOSP-B");
        notification.setSourceHospitalId("HOSP-B");
        notification.setTargetHospitalId("HOSP-B");
        notification.setDataTypes(
                pushRequest.getDataTypes() != null
                        ? String.join(",", pushRequest.getDataTypes()) : "");
        notification.setFhirBundleJson(payload);
        notification.setFhirBundleHash(auditService.hashPayload(payload));
        notification.setRead(false);
        pushNotificationRepository.save(notification);

        return payload;
    }

    // ── Notification Support ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PatientPushNotification> getNotificationsForDoctor(String doctorUsername) {
        return pushNotificationRepository
                .findByTargetDoctorUsernameAndHospitalCodeOrderByPushedAtDesc(
                        doctorUsername, "HOSP-B");
    }

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
}
