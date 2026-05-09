package com.fhir.doctor.controller;

import com.fhir.doctor.dto.DoctorPatientLookupResponseDTO;
import com.fhir.doctor.service.DoctorPatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/doctor/patients")
public class DoctorPatientController {

    @Autowired
    private DoctorPatientService doctorPatientService;

    @GetMapping("/lookup/{identifier}")
    public DoctorPatientLookupResponseDTO lookupPatient(@PathVariable String identifier) {
        return doctorPatientService.lookupPatient(identifier);
    }

    @PostMapping("/link/{abhaId}")
    public Map<String, String> linkPatientByAbhaId(@PathVariable String abhaId) {
        return doctorPatientService.linkPatientByAbhaId(abhaId);
    }
}
