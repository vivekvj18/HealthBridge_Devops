package com.fhir.hospitalB.controller;

import com.fhir.hospitalB.service.HospitalBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/internal/hospitalB")
public class HospitalBInternalController {

    @Autowired
    private HospitalBService hospitalBService;

    @PostMapping("/fhir-bundle")
    public String pullFhirBundle(@RequestBody PullBundleRequest request) {
        return hospitalBService.pullFhirBundle(
                request.patientId(),
                request.consentToken(),
                request.scope());
    }

    public record PullBundleRequest(String patientId, String consentToken, Set<String> scope) {
    }
}
