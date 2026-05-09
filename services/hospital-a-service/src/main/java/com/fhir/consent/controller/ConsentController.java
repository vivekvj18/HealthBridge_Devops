package com.fhir.consent.controller;

import com.fhir.consent.dto.ConsentDecisionDTO;
import com.fhir.consent.dto.ConsentRequestViewDTO;
import com.fhir.consent.dto.InitiateConsentDTO;
import com.fhir.consent.service.ConsentStore;
import com.fhir.shared.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTTP adapter for consent lifecycle endpoints.
 * <p>
 * Delegates all business logic to {@link ConsentStore}.  Auth context
 * extraction is centralised via {@link SecurityContextHelper}.
 */
@RestController
@RequestMapping("/consent")
public class ConsentController {

    @Autowired
    private ConsentStore consentStore;

    @Autowired
    private SecurityContextHelper securityContextHelper;

    // ── Initiate consent request (Requester) ─────────────────────────────────

    @PostMapping("/initiate")
    public ConsentRequestViewDTO initiateConsent(@RequestBody InitiateConsentDTO initiateDTO) {
        String requesterId = securityContextHelper.getCurrentUsername();
        return consentStore.initiateRequest(initiateDTO, requesterId);
    }

    // ── View pending requests (Patient) ──────────────────────────────────────

    @GetMapping("/pending/{patientId}")
    public List<ConsentRequestViewDTO> getPendingRequests(@PathVariable String patientId) {
        // Optionally validate patientId against securityContextHelper.extractPatientId()
        return consentStore.getPendingRequests(patientId);
    }

    // ── Respond to request (Patient) ─────────────────────────────────────────

    @PostMapping("/respond/{requestId}")
    public ConsentRequestViewDTO respondToRequest(
            @PathVariable Long requestId,
            @RequestBody ConsentDecisionDTO decisionDTO) {
        String patientId = securityContextHelper.extractAbhaId();
        return consentStore.processDecision(requestId, patientId, decisionDTO);
    }

    // ── Revoke consent (Patient) ─────────────────────────────────────────────

    @PostMapping("/revoke/{requestId}")
    public String revokeConsent(@PathVariable Long requestId) {
        String patientId = securityContextHelper.extractAbhaId();
        consentStore.revoke(requestId, patientId);
        return "Consent REVOKED for request ID: " + requestId;
    }
}
