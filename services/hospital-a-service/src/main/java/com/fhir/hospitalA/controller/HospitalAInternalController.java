package com.fhir.hospitalA.controller;

import com.fhir.hospitalA.service.HospitalAService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/internal/hospitalA")
public class HospitalAInternalController {

    @Autowired
    private HospitalAService hospitalAService;

    @PostMapping("/fhir-bundle")
    public String pullFhirBundle(@RequestBody PullBundleRequest request) {
        return hospitalAService.pullFhirBundle(
                request.patientId(),
                request.consentToken(),
                request.scope());
    }

    public record PullBundleRequest(String patientId, String consentToken, Set<String> scope) {
    }
}
