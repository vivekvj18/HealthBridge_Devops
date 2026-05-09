package com.fhir.hospitalA.mapper;

import com.fhir.hospitalA.dto.HospitalAOPConsultRecordDTO;
import org.hl7.fhir.r4.model.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

public class HospitalAOPConsultToFhirMapper {
    private static final DateTimeFormatter VISIT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static Bundle mapToBundle(HospitalAOPConsultRecordDTO dto) {

        // ── Patient ──────────────────────────────────────────────────────────
        Patient patient = new Patient();
        patient.setId(dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId());
        HumanName name = new HumanName();
        name.setFamily(dto.getPatientLastName());
        name.addGiven(dto.getPatientFirstName());
        patient.addName(name);

        // ── Practitioner ─────────────────────────────────────────────────────
        Practitioner practitioner = new Practitioner();
        practitioner.setId("PR-" + UUID.randomUUID().toString());
        HumanName docName = new HumanName();
        docName.setText(dto.getDoctorName());
        practitioner.addName(docName);

        // ── Encounter ────────────────────────────────────────────────────────
        Date visitDate;
        try {
            // Try ISO format first (yyyy-MM-dd from seeder), then dd/MM/yyyy from frontend
            LocalDate parsed;
            try {
                parsed = LocalDate.parse(dto.getVisitDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e1) {
                parsed = LocalDate.parse(dto.getVisitDate(), VISIT_DATE_FORMAT);
            }
            visitDate = Date.from(parsed.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            // If visitDate is null or malformed, fall back to today
            visitDate = new Date();
        }

        Encounter encounter = new Encounter();
        encounter.setId(UUID.randomUUID().toString());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(
                new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                        .setCode("AMB")
                        .setDisplay("Ambulatory")
        );
        encounter.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));
        encounter.setPeriod(new Period().setStart(visitDate)); // FIX applied here
        encounter.addParticipant()
                .setIndividual(new Reference("Practitioner/" + practitioner.getIdPart()));

        // ── Observation: Temperature ─────────────────────────────────────────
        Observation temperatureObs = new Observation();
        temperatureObs.setId(UUID.randomUUID().toString());
        temperatureObs.setStatus(Observation.ObservationStatus.FINAL);
        temperatureObs.setCode(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("8310-5")
                        .setDisplay("Body temperature")
        ));
        temperatureObs.setValue(
                new Quantity()
                        .setValue(dto.getTemperature())
                        .setUnit("F")
                        .setSystem("http://unitsofmeasure.org")
                        .setCode("[degF]")
        );
        temperatureObs.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));

        // ── Observation: Blood Pressure ──────────────────────────────────────
        Observation bpObs = new Observation();
        bpObs.setId(UUID.randomUUID().toString());
        bpObs.setStatus(Observation.ObservationStatus.FINAL);
        bpObs.setCode(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("85354-9")
                        .setDisplay("Blood pressure panel")
        ));
        bpObs.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));

        String[] bpParts = dto.getBloodPressure().split("/");
        int systolicValue  = Integer.parseInt(bpParts[0].trim());
        int diastolicValue = Integer.parseInt(bpParts[1].trim());

        Observation.ObservationComponentComponent systolic =
                new Observation.ObservationComponentComponent();
        systolic.setCode(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("8480-6")
                        .setDisplay("Systolic blood pressure")
        ));
        systolic.setValue(
                new Quantity()
                        .setValue(systolicValue)
                        .setUnit("mmHg")
                        .setSystem("http://unitsofmeasure.org")
                        .setCode("mm[Hg]")
        );

        Observation.ObservationComponentComponent diastolic =
                new Observation.ObservationComponentComponent();
        diastolic.setCode(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("8462-4")
                        .setDisplay("Diastolic blood pressure")
        ));
        diastolic.setValue(
                new Quantity()
                        .setValue(diastolicValue)
                        .setUnit("mmHg")
                        .setSystem("http://unitsofmeasure.org")
                        .setCode("mm[Hg]")
        );

        bpObs.addComponent(systolic);
        bpObs.addComponent(diastolic);

        // ── Observation: Symptoms ────────────────────────────────────────────
        Observation symptomsObs = new Observation();
        symptomsObs.setId(UUID.randomUUID().toString());
        symptomsObs.setStatus(Observation.ObservationStatus.FINAL);
        symptomsObs.setCode(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("75325-1")
                        .setDisplay("Symptoms")
        ));
        symptomsObs.setValue(new StringType(dto.getSymptoms()));
        symptomsObs.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));

        // ── DocumentReference (PDF prescription) ─────────────────────────────
        DocumentReference docRef = null;
        if (dto.getPrescriptionPdfBase64() != null
                && !dto.getPrescriptionPdfBase64().isBlank()) {
            try {
                byte[] pdfBytes = Base64.getDecoder().decode(
                        dto.getPrescriptionPdfBase64().trim());
                docRef = new DocumentReference();
                docRef.setId(UUID.randomUUID().toString());
                docRef.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
                CodeableConcept docType = new CodeableConcept();
                docType.addCoding()
                        .setSystem("http://loinc.org")
                        .setCode("60591-5")
                        .setDisplay("Prescription Document");
                docRef.setType(docType);
                docRef.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));

                Attachment attachment = new Attachment();
                attachment.setContentType("application/pdf");
                attachment.setData(pdfBytes);
                docRef.addContent().setAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // Invalid Base64 — skip PDF attachment and do not create DocumentReference
                docRef = null;
            }
        }

        // ── Consent ────────────────────────────────────────────────────────────────────
        // The patient's consent decision travels inside the Bundle.
        // Hospital B can independently verify that consent was granted
        // before accepting or processing the received data.
        // status=ACTIVE means consent is currently valid.
        // provision.type=PERMIT means data transfer is allowed.
        Consent consent = new Consent();
        consent.setId(UUID.randomUUID().toString());
        consent.setStatus(Consent.ConsentState.ACTIVE);

        // Scope: patient-privacy — covers sharing of personal health data
        CodeableConcept consentScope = new CodeableConcept();
        consentScope.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/consentscope")
                .setCode("patient-privacy")
                .setDisplay("Privacy Consent");
        consent.setScope(consentScope);

        // Category: LOINC 59284-0 = Patient Consent document
        consent.addCategory(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("59284-0")
                        .setDisplay("Patient Consent")
        ));

        // Link consent to this patient
        consent.setPatient(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));

        // Timestamp of when consent was granted
        consent.setDateTime(new Date());

        // Policy: OPTIN — patient has actively opted in to data sharing
        consent.setPolicyRule(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                        .setCode("OPTIN")
                        .setDisplay("opt-in")
        ));

        // Provision: PERMIT — data transfer is permitted
        Consent.provisionComponent provision = new Consent.provisionComponent();
        provision.setType(Consent.ConsentProvisionType.PERMIT);
        consent.setProvision(provision);

        // ── Bundle: assemble all resources ───────────────────────────────────
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);

        addEntry(bundle, patient);
        addEntry(bundle, practitioner);
        addEntry(bundle, encounter);
        addEntry(bundle, temperatureObs);
        addEntry(bundle, bpObs);
        addEntry(bundle, symptomsObs);
        if (docRef != null) {
            addEntry(bundle, docRef);
        }
        addEntry(bundle, consent);
        return bundle;
    }

    private static void addEntry(Bundle bundle, Resource resource) {
        if (resource == null) return;
        String idPart = resource.getIdElement().getIdPart();
        bundle.addEntry()
                .setFullUrl("https://health-bridge.local/fhir/" + resource.getResourceType().name() + "/" + idPart)
                .setResource(resource);
    }
}
