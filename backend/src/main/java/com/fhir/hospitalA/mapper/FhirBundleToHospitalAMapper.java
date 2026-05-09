package com.fhir.hospitalA.mapper;

import com.fhir.hospitalA.dto.HospitalAOPConsultRecordDTO;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class FhirBundleToHospitalAMapper {

    public static HospitalAOPConsultRecordDTO map(Bundle bundle) {
        HospitalAOPConsultRecordDTO dto = new HospitalAOPConsultRecordDTO();

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();

            if (resource instanceof Patient patient) {
                String identifier = extractPatientIdentifier(patient);
                dto.setPatientId(identifier);
                dto.setAbhaId(extractAbhaId(patient, identifier));

                // Prefer FHIR given/family fields so we don't re-split a joined string
                HumanName name = patient.getName().isEmpty() ? null : patient.getNameFirstRep();
                if (name != null) {
                    // First name = first given name element
                    String given = name.getGiven().stream()
                            .map(StringType::getValue)
                            .filter(v -> v != null && !v.isBlank())
                            .findFirst().orElse("");
                    // Last name = family element
                    String family = name.hasFamily() ? name.getFamily() : "";

                    if (!given.isBlank() || !family.isBlank()) {
                        dto.setPatientFirstName(given.isBlank() ? family : given);
                        dto.setPatientLastName(family.isBlank() ? "" : family);
                    } else if (name.hasText() && !name.getText().isBlank()) {
                        // Fallback: split full-text on first whitespace
                        String[] parts = name.getText().trim().split("\\s+", 2);
                        dto.setPatientFirstName(parts[0]);
                        dto.setPatientLastName(parts.length > 1 ? parts[1] : "");
                    }
                }
            }

            if (resource instanceof Practitioner practitioner && !practitioner.getName().isEmpty()) {
                HumanName name = practitioner.getNameFirstRep();
                dto.setDoctorName(name.hasText() ? name.getText() : extractHumanName(name));
            }

            if (resource instanceof Encounter encounter
                    && encounter.getPeriod() != null
                    && encounter.getPeriod().getStart() != null) {
                // Hospital A native format: ISO date yyyy-MM-dd (matches seeder + DB)
                DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                dto.setVisitDate(
                        encounter.getPeriod().getStart().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(iso)
                );
            }

            if (resource instanceof Observation observation
                    && observation.getCode() != null
                    && !observation.getCode().getCoding().isEmpty()) {
                String code = observation.getCode().getCodingFirstRep().getCode();

                if ("8310-5".equals(code) && observation.getValue() instanceof Quantity q && q.getValue() != null) {
                    dto.setTemperature(q.getValue().doubleValue());
                }

                if ("85354-9".equals(code)) {
                    StringBuilder bp = new StringBuilder();
                    for (Observation.ObservationComponentComponent component : observation.getComponent()) {
                        String componentCode = component.getCode().getCodingFirstRep().getCode();
                        if ("8480-6".equals(componentCode) && component.getValue() instanceof Quantity q && q.getValue() != null) {
                            bp.append(q.getValue().intValue());
                        }
                        if ("8462-4".equals(componentCode) && component.getValue() instanceof Quantity q && q.getValue() != null) {
                            bp.append("/").append(q.getValue().intValue());
                        }
                    }
                    dto.setBloodPressure(bp.toString());
                }

                if (("75325-1".equals(code) || "34109-1".equals(code))
                        && observation.getValue() instanceof StringType s) {
                    dto.setSymptoms(s.getValue());
                }
            }

            if (resource instanceof DocumentReference docRef
                    && docRef.getContentFirstRep() != null
                    && docRef.getContentFirstRep().getAttachment() != null) {
                byte[] pdfBytes = docRef.getContentFirstRep().getAttachment().getData();
                if (pdfBytes != null) {
                    dto.setPrescriptionPdfBase64(Base64.getEncoder().encodeToString(pdfBytes));
                }
            }
        }

        return dto;
    }

    private static String extractPatientIdentifier(Patient patient) {
        for (Identifier identifier : patient.getIdentifier()) {
            if (identifier.hasValue() && identifier.getValue() != null && !identifier.getValue().isBlank()) {
                return identifier.getValue();
            }
        }

        if (patient.getIdElement() != null && patient.getIdElement().getIdPart() != null && !patient.getIdElement().getIdPart().isBlank()) {
            return patient.getIdElement().getIdPart();
        }

        return patient.getId();
    }

    private static String extractAbhaId(Patient patient, String fallbackId) {
        for (Identifier identifier : patient.getIdentifier()) {
            String value = identifier.getValue();
            if (value != null && value.startsWith("ABHA-")) {
                return value;
            }
        }
        return fallbackId;
    }

    private static String extractHumanName(HumanName name) {
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

        return parts.isEmpty() ? null : String.join(" ", parts);
    }
}
