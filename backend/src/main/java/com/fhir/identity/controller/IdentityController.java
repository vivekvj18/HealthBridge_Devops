package com.fhir.identity.controller;

import com.fhir.identity.dto.RegisterPatientDTO;
import com.fhir.identity.model.PatientProfile;
import com.fhir.identity.service.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    @Autowired
    private IdentityService identityService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientProfile register(@RequestBody RegisterPatientDTO dto) {
        return identityService.register(dto);
    }
}
