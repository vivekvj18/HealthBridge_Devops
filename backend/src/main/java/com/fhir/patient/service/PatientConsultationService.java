package com.fhir.patient.service;

import com.fhir.hospitalA.model.HospitalAOPConsultEntity;
import com.fhir.hospitalA.service.HospitalAService;
import com.fhir.hospitalB.model.HospitalBOPConsultEntity;
import com.fhir.hospitalB.service.HospitalBService;
import com.fhir.patient.dto.PatientConsultationSummaryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PatientConsultationService {

    @Autowired
    private HospitalAService hospitalAService;

    @Autowired
    private HospitalBService hospitalBService;

    @Transactional(readOnly = true)
    public List<PatientConsultationSummaryDTO> getConsultations(String abhaId) {
        List<PatientConsultationSummaryDTO> consultations = new ArrayList<>();

        for (HospitalAOPConsultEntity consult : hospitalAService.getConsultsByAbhaId(abhaId)) {
            consultations.add(new PatientConsultationSummaryDTO(
                "HA-" + consult.getId(),
                "City General Hospital",
                consult.getDoctorName(),
                consult.getVisitDate(),
                consult.getCreatedAt() != null ? consult.getCreatedAt().toString() : null,
                consult.getSymptoms(),
                consult.getBloodPressure(),
                Double.toString(consult.getTemperature()),
                hasText(consult.getPrescriptionPdfBase64())
            ));
        }

        for (HospitalBOPConsultEntity consult : hospitalBService.getConsultsByAbhaId(abhaId)) {
            consultations.add(new PatientConsultationSummaryDTO(
                "HB-" + consult.getId(),
                "Metro Medical Center",
                consult.getDoctor(),
                consult.getConsultDate(),
                consult.getReceivedAt() != null ? consult.getReceivedAt().toString() : null,
                consult.getClinicalNotes(),
                consult.getBloodPressure(),
                consult.getTemperature(),
                hasText(consult.getPrescriptionPdfBase64())
            ));
        }

        consultations.sort(Comparator.comparing(
            PatientConsultationSummaryDTO::getRecordedAt,
            Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed());

        return consultations;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
