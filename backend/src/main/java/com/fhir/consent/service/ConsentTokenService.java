package com.fhir.consent.service;

import com.fhir.consent.model.ConsentRequestEntity;
import com.fhir.shared.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

@Service
public class ConsentTokenService {

    @Autowired
    private JwtUtil jwtUtil;

    // Consent tokens are valid for 1 hour
    private static final long CONSENT_TOKEN_EXPIRY_SECONDS = 3600;

    public String generateConsentToken(ConsentRequestEntity consent) {
        Map<String, Object> claims = Map.of(
            "sub",         consent.getPatientId(),
            "requesterId", consent.getRequesterId(),
            "scope",       new ArrayList<>(consent.getGrantedDataTypes()),
            "consentId",   consent.getId(),
            "type",        "consent"
        );
        return jwtUtil.sign(claims, CONSENT_TOKEN_EXPIRY_SECONDS, consent.getPatientId());
    }
}
