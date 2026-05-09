package com.fhir.hospitalB.mapper;

import com.fhir.hospitalB.dto.HospitalBOPConsultRecordDTO;
import org.hl7.fhir.r4.model.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class FhirBundleToHospitalBMapper {
    public static HospitalBOPConsultRecordDTO map(Bundle bundle) {

        HospitalBOPConsultRecordDTO dto = new HospitalBOPConsultRecordDTO();
        HospitalBOPConsultRecordDTO.Vitals vitals = new HospitalBOPConsultRecordDTO.Vitals();

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();

            // ── Patient ──────────────────────────────────────────────────────
            if (resource instanceof Patient patient) {
                String patientIdentifier = extractPatientIdentifier(patient);
                dto.setAbhaId(extractAbhaId(patient, patientIdentifier));
                dto.setPatientId(patientIdentifier);
                dto.setUhid(patientIdentifier);
                dto.setPatientName(extractPatientName(patient));
            }

            // ── Practitioner ─────────────────────────────────────────────────
            if (resource instanceof Practitioner practitioner) {
                if (!practitioner.getName().isEmpty()) {
                    dto.setDoctor(practitioner.getNameFirstRep().getText());
                }
            }

            // ── Encounter ────────────────────────────────────────────────────
            if (resource instanceof Encounter encounter) {
                if (encounter.getPeriod() != null
                        && encounter.getPeriod().getStart() != null) {
                    // Hospital B native format: ISO date yyyy-MM-dd
                    DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    dto.setConsultDate(
                            encounter.getPeriod().getStart().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(iso)
                    );
                }
            }

            // ── Observations ─────────────────────────────────────────────────
            if (resource instanceof Observation obs) {
                if (obs.getCode() != null && !obs.getCode().getCoding().isEmpty()) {
                    String code = obs.getCode().getCodingFirstRep().getCode();

                    // Temperature — store as plain decimal string (Hospital B native: "40.0")
                    if ("8310-5".equals(code)) {
                        if (obs.getValue() instanceof Quantity q && q.getValue() != null) {
                            // Strip unit — Hospital B stores temperature as a bare numeric string
                            vitals.setTemp(q.getValue().stripTrailingZeros().toPlainString());
                        }
                    }
                    if ("85354-9".equals(code)) {
                        StringBuilder bp = new StringBuilder();
                        for (Observation.ObservationComponentComponent component
                                : obs.getComponent()) {

                            String compCode = component.getCode()
                                    .getCodingFirstRep().getCode();

                            if ("8480-6".equals(compCode)
                                    && component.getValue() instanceof Quantity q) {
                                bp.append(q.getValue().intValue()); // systolic first
                            }
                            if ("8462-4".equals(compCode)
                                    && component.getValue() instanceof Quantity q) {
                                bp.append("/").append(q.getValue().intValue());
                            }
                        }
                        vitals.setBp(bp.toString());
                    }

                    // Symptoms
                    if ("75325-1".equals(code) || "34109-1".equals(code)) {
                        if (obs.getValue() instanceof StringType s) {
                            dto.setClinicalNotes(s.getValue());
                        }
                    }
                }
            }

            // ── DocumentReference — PDF extraction ───────────────────────────
            if (resource instanceof DocumentReference docRef) {
                if (docRef.getContentFirstRep() != null
                        && docRef.getContentFirstRep().getAttachment() != null) {
                    byte[] pdfBytes = docRef.getContentFirstRep()
                            .getAttachment().getData();
                    if (pdfBytes != null) {
                        dto.setPrescriptionPdfBase64(
                                Base64.getEncoder().encodeToString(pdfBytes)
                        );
                    }
                }
            }

            // ── Consent verification ─────────────────────────────────────────
            // Hospital B independently verifies that a valid Consent exists
            // inside the received Bundle before trusting the data.
            // If consent is missing or not ACTIVE + PERMIT, we flag it.
            if (resource instanceof Consent consent) {
                boolean isActive = Consent.ConsentState.ACTIVE
                        .equals(consent.getStatus());
                boolean isPermit = consent.getProvision() != null
                        && Consent.ConsentProvisionType.PERMIT
                        .equals(consent.getProvision().getType());

                if (isActive && isPermit) {
                    dto.setConsentVerified(true);
                } else {
                    dto.setConsentVerified(false);
                }
            }
        }
        dto.setVitals(vitals);
        return dto;
    }

    private static String extractPatientIdentifier(Patient patient) {
        if (!patient.getIdentifier().isEmpty()) {
            for (Identifier identifier : patient.getIdentifier()) {
                if (identifier.hasValue() && identifier.getValue() != null && !identifier.getValue().isBlank()) {
                    return identifier.getValue();
                }
            }
        }

        if (patient.getIdElement() != null) {
            String idPart = patient.getIdElement().getIdPart();
            if (idPart != null && !idPart.isBlank()) {
                return idPart;
            }
        }

        String id = patient.getId();
        return (id != null && !id.isBlank()) ? id : null;
    }

    private static String extractAbhaId(Patient patient, String fallbackId) {
        if (!patient.getIdentifier().isEmpty()) {
            for (Identifier identifier : patient.getIdentifier()) {
                String value = identifier.getValue();
                if (value != null && value.startsWith("ABHA-")) {
                    return value;
                }
            }
        }

        if (fallbackId != null && fallbackId.startsWith("ABHA-")) {
            return fallbackId;
        }

        return fallbackId;
    }

    private static String extractPatientName(Patient patient) {
        if (patient.getName().isEmpty()) {
            return null;
        }

        HumanName name = patient.getNameFirstRep();

        if (name.hasText() && name.getText() != null && !name.getText().isBlank()) {
            return name.getText();
        }

        List<String> parts = name.getGiven().stream()
                .map(StringType::getValue)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toList());

        if (name.hasFamily() && name.getFamily() != null && !name.getFamily().isBlank()) {
            parts.add(name.getFamily());
        }

        if (parts.isEmpty()) {
            return null;
        }

        return String.join(" ", parts);
    }
}
