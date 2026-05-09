package com.fhir.auth.controller;

import com.fhir.auth.dto.RegisterRequest;
import com.fhir.auth.service.PatientRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/register/patient")
public class PatientRegistrationController {

    @Autowired
    private PatientRegistrationService patientRegistrationService;

    @PostMapping
    public Map<String, Object> registerPatient(@RequestBody RegisterRequest request) {
        return patientRegistrationService.registerPatient(request);
    }

    @GetMapping("/{abhaId}")
    public Map<String, Object> getPatientByAbhaId(@PathVariable String abhaId) {
        return patientRegistrationService.getPatientByAbhaId(abhaId);
    }
}
