package com.fhir.hospitalB.mapper;

import com.fhir.hospitalB.dto.HospitalBOPConsultRecordDTO;
import org.hl7.fhir.r4.model.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

public class HospitalBOPConsultToFhirMapper {
    private static final DateTimeFormatter VISIT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static Bundle mapToBundle(HospitalBOPConsultRecordDTO dto) {

        // ── Patient ──────────────────────────────────────────────────────────
        Patient patient = new Patient();
        patient.setId(dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId());
        HumanName name = new HumanName();
        // Hospital B has a single 'patientName' field, so we just use text
        name.setText(dto.getPatientName());
        patient.addName(name);

        // ── Practitioner ─────────────────────────────────────────────────────
        Practitioner practitioner = new Practitioner();
        practitioner.setId("PR-" + UUID.randomUUID().toString());
        HumanName docName = new HumanName();
        docName.setText(dto.getDoctor());
        practitioner.addName(docName);

        // ── Encounter ────────────────────────────────────────────────────────
        Date visitDate;
        try {
            LocalDate parsed;
            try {
                parsed = LocalDate.parse(dto.getConsultDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e1) {
                parsed = LocalDate.parse(dto.getConsultDate(), VISIT_DATE_FORMAT);
            }
            visitDate = Date.from(parsed.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
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
        encounter.setPeriod(new Period().setStart(visitDate));
        encounter.addParticipant()
                .setIndividual(new Reference("Practitioner/" + practitioner.getIdPart()));

        // ── Observation: Temperature ─────────────────────────────────────────
        Observation temperatureObs = new Observation();
        if (dto.getVitals() != null && dto.getVitals().getTemp() != null && !dto.getVitals().getTemp().isBlank()) {
            temperatureObs.setId(UUID.randomUUID().toString());
            temperatureObs.setStatus(Observation.ObservationStatus.FINAL);
            temperatureObs.setCode(new CodeableConcept().addCoding(
                    new Coding()
                            .setSystem("http://loinc.org")
                            .setCode("8310-5")
                            .setDisplay("Body temperature")
            ));
            try {
                temperatureObs.setValue(
                        new Quantity()
                                .setValue(Double.parseDouble(dto.getVitals().getTemp()))
                                .setUnit("F")
                                .setSystem("http://unitsofmeasure.org")
                                .setCode("[degF]")
                );
            } catch (NumberFormatException e) {
                temperatureObs.setValue(new StringType(dto.getVitals().getTemp()));
            }
            temperatureObs.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));
        }

        // ── Observation: Blood Pressure ──────────────────────────────────────
        Observation bpObs = new Observation();
        if (dto.getVitals() != null && dto.getVitals().getBp() != null && !dto.getVitals().getBp().isBlank()) {
            bpObs.setId(UUID.randomUUID().toString());
            bpObs.setStatus(Observation.ObservationStatus.FINAL);
            bpObs.setCode(new CodeableConcept().addCoding(
                    new Coding()
                            .setSystem("http://loinc.org")
                            .setCode("85354-9")
                            .setDisplay("Blood pressure panel")
            ));
            bpObs.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));

            try {
                String[] bpParts = dto.getVitals().getBp().split("/");
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
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        // ── Observation: Clinical Notes ────────────────────────────────────────────
        Observation notesObs = new Observation();
        notesObs.setId(UUID.randomUUID().toString());
        notesObs.setStatus(Observation.ObservationStatus.FINAL);
        notesObs.setCode(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("34109-1")
                        .setDisplay("Note")
        ));
        notesObs.setValue(new StringType(dto.getClinicalNotes()));
        notesObs.setSubject(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));

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
            } catch (IllegalArgumentException ignored) {}
        }

        // ── Consent ────────────────────────────────────────────────────────────────────
        Consent consent = new Consent();
        consent.setId(UUID.randomUUID().toString());
        consent.setStatus(Consent.ConsentState.ACTIVE);

        CodeableConcept consentScope = new CodeableConcept();
        consentScope.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/consentscope")
                .setCode("patient-privacy")
                .setDisplay("Privacy Consent");
        consent.setScope(consentScope);

        consent.addCategory(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("59284-0")
                        .setDisplay("Patient Consent")
        ));

        consent.setPatient(new Reference("Patient/" + (dto.getAbhaId() != null ? dto.getAbhaId() : dto.getPatientId())));
        consent.setDateTime(new Date());

        consent.setPolicyRule(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                        .setCode("OPTIN")
                        .setDisplay("opt-in")
        ));

        Consent.provisionComponent provision = new Consent.provisionComponent();
        provision.setType(Consent.ConsentProvisionType.PERMIT);
        consent.setProvision(provision);

        // ── Bundle ───────────────────────────────────────────────────────────
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);

        addEntry(bundle, patient);
        addEntry(bundle, practitioner);
        addEntry(bundle, encounter);
        if (temperatureObs.hasCode()) addEntry(bundle, temperatureObs);
        if (bpObs.hasCode()) addEntry(bundle, bpObs);
        addEntry(bundle, notesObs);
        if (docRef != null) addEntry(bundle, docRef);
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
