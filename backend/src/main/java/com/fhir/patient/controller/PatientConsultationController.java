package com.fhir.patient.controller;

import com.fhir.patient.dto.PatientConsultationSummaryDTO;
import com.fhir.patient.service.PatientConsultationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/patient/consultations")
public class PatientConsultationController {

    @Autowired
    private PatientConsultationService patientConsultationService;

    @GetMapping("/{abhaId}")
    public List<PatientConsultationSummaryDTO> getConsultations(@PathVariable String abhaId) {
        return patientConsultationService.getConsultations(abhaId);
    }
}
