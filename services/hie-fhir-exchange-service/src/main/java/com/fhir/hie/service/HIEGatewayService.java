package com.fhir.hie.service;

import com.fhir.consent.dto.ConsentRequestViewDTO;
import com.fhir.consent.dto.InitiateConsentDTO;
import com.fhir.consent.model.ConsentRequestEntity;
import com.fhir.consent.model.ConsentStatus;
import com.fhir.consent.repository.ConsentRequestRepository;
import com.fhir.consent.service.ConsentStore;
import com.fhir.hie.client.HIPFhirClient;
import com.fhir.hie.dto.ExchangeRequestDTO;
import com.fhir.hie.dto.ExchangeResponseDTO;
import com.fhir.identity.service.IdentityService;
import com.fhir.hospitalA.repository.HospitalAOPConsultRepository;
import com.fhir.hospitalB.repository.HospitalBOPConsultRepository;
import com.fhir.notification.NotificationService;
import com.fhir.shared.audit.AuditService;
import com.fhir.shared.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.time.Instant;

@Service
public class HIEGatewayService {

    @Autowired private IdentityService identityService;
    @Autowired private ConsentStore consentStore;
    @Autowired private ConsentRequestRepository consentRequestRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private HIPFhirClient hipFhirClient;
    @Autowired private AuditService auditService;
    @Autowired private SecurityContextHelper securityContextHelper;
    @Autowired private HospitalAOPConsultRepository hospitalAOPConsultRepository;
    @Autowired private HospitalBOPConsultRepository hospitalBOPConsultRepository;

    public ExchangeResponseDTO orchestrateExchange(ExchangeRequestDTO request) {
        String requesterId = securityContextHelper.getCurrentUsername();

        // Step 1: Identity resolution
        if (!identityService.exists(request.getPatientId())) {
            return ExchangeResponseDTO.builder()
                .status("IDENTITY_NOT_FOUND")
                .message("Patient " + request.getPatientId() +
                         " is not registered in the identity service.")
                .build();
        }

        // Step 2: Check for existing active GRANTED consent
        Set<String> grantedTypes = consentStore.getActiveGrantedDataTypes(
            request.getPatientId(), requesterId);

        if (!grantedTypes.isEmpty()) {
            try {
                // Consent exists — try to pull data
                return pullAndReturn(request, requesterId, grantedTypes);
            } catch (Exception e) {
                // If pull fails (e.g. token expired/invalid), fallback to initiating a new request
                System.out.println("⚠️ Existing consent failed (likely expired). Initiating fresh request. Error: " + e.getMessage());
            }
        }

        // Step 3: No consent — create request and notify patient
        request.setHip(resolveHipForPatient(request.getPatientId(), request.getHip()));
        if (request.getHiu() == null || request.getHiu().isBlank()) {
            request.setHiu(resolveHiuForCurrentRequester());
        }
        InitiateConsentDTO initiateDTO = new InitiateConsentDTO();
        initiateDTO.setPatientId(request.getPatientId());
        initiateDTO.setRequesterHospitalId(resolveHospitalIdFromHiu(request.getHiu()));
        initiateDTO.setProviderHospitalId(resolveHospitalIdFromHip(request.getHip()));
        initiateDTO.setPurpose(request.getPurpose() != null
            ? request.getPurpose()
            : "HIE Data Exchange requested by " + request.getHiu());
        initiateDTO.setRequestedDataTypes(request.getScope());

        ConsentRequestViewDTO consent = consentStore.initiateRequest(
            initiateDTO, requesterId);

        // Step 4: Notify patient
        notificationService.notifyPatientConsentRequest(
            request.getPatientId(), consent.getId());

        return ExchangeResponseDTO.builder()
            .status("CONSENT_PENDING")
            .consentRequestId(consent.getId())
            .message("Consent request #" + consent.getId() +
                     " sent to patient. Poll GET /hie/exchange/status/" +
                     consent.getId() + " until status is SUCCESS.")
            .build();
    }

    public ExchangeResponseDTO checkExchangeStatus(Long consentId) {
        ConsentRequestEntity consent = consentRequestRepository.findById(consentId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Consent request not found: " + consentId));

        if (consent.getStatus() == ConsentStatus.GRANTED) {
            ExchangeRequestDTO req = new ExchangeRequestDTO();
            req.setPatientId(consent.getPatientId());
            req.setHip(resolveHipForPatient(consent.getPatientId(), resolveHipForCurrentRequester()));
            req.setHiu(resolveHiuForCurrentRequester());
            req.setScope(consent.getGrantedDataTypes());

            return pullAndReturn(req, consent.getRequesterId(),
                consent.getGrantedDataTypes());
        }

        if (consent.getStatus() == ConsentStatus.DENIED ||
            consent.getStatus() == ConsentStatus.REVOKED) {
            return ExchangeResponseDTO.builder()
                .status(consent.getStatus().name())
                .message("Patient has " + consent.getStatus().name().toLowerCase() +
                         " this consent request.")
                .build();
        }

        return ExchangeResponseDTO.builder()
            .status("CONSENT_PENDING")
            .consentRequestId(consentId)
            .message("Still waiting for patient approval.")
            .build();
    }

    /**
     * Phase 1: Explicitly request consent from patient.
     */
    public ExchangeResponseDTO initiateConsentOnly(ExchangeRequestDTO request) {
        String requesterId = securityContextHelper.getCurrentUsername();
        request.setHip(resolveHipForPatient(request.getPatientId(), request.getHip()));
        if (request.getHiu() == null || request.getHiu().isBlank()) {
            request.setHiu(resolveHiuForCurrentRequester());
        }
        
        InitiateConsentDTO initiateDTO = new InitiateConsentDTO();
        initiateDTO.setPatientId(request.getPatientId());
        initiateDTO.setRequesterHospitalId(resolveHospitalIdFromHiu(request.getHiu()));
        initiateDTO.setProviderHospitalId(resolveHospitalIdFromHip(request.getHip()));
        initiateDTO.setPurpose(request.getPurpose() != null ? request.getPurpose() : "Manual HIE Consent Request");
        initiateDTO.setRequestedDataTypes(request.getScope());

        ConsentRequestViewDTO consent = consentStore.initiateRequest(initiateDTO, requesterId);
        notificationService.notifyPatientConsentRequest(request.getPatientId(), consent.getId());

        return ExchangeResponseDTO.builder()
            .status("CONSENT_PENDING")
            .consentRequestId(consent.getId())
            .message("Consent request #" + consent.getId() + " sent. Ask patient to approve.")
            .build();
    }

    /**
     * Phase 2: Pull data only if consent is already GRANTED.
     */
    public ExchangeResponseDTO pullClinicalData(ExchangeRequestDTO request) {
        String requesterId = securityContextHelper.getCurrentUsername();

        Set<String> grantedTypes = consentStore.getActiveGrantedDataTypes(request.getPatientId(), requesterId);
        if (grantedTypes.isEmpty()) {
            return ExchangeResponseDTO.builder()
                .status("NO_CONSENT")
                .message("No active consent found. Please request consent first.")
                .build();
        }

        request.setHip(resolveHipForPatient(request.getPatientId(), request.getHip()));
        request.setHiu(resolveHiuForCurrentRequester());
        return pullAndReturn(request, requesterId, grantedTypes);
    }

    private ExchangeResponseDTO pullAndReturn(
            ExchangeRequestDTO request,
            String requesterId,
            Set<String> grantedTypes) {

        request.setHip(resolveHipForPatient(request.getPatientId(), request.getHip()));
        if (request.getHiu() == null || request.getHiu().isBlank()) {
            request.setHiu(resolveHiuForCurrentRequester());
        }

        // Fetch the consent token from the latest GRANTED consent
        // Fetch the latest GRANTED consent (highest ID)
        ConsentRequestEntity latestConsent = consentRequestRepository
            .findFirstByPatientIdAndRequesterIdAndStatusOrderByIdDesc(
                request.getPatientId(), requesterId, ConsentStatus.GRANTED)
            .orElse(null);

        if (latestConsent == null) {
            return ExchangeResponseDTO.builder()
                .status("NO_CONSENT")
                .message("No consent found.")
                .build();
        }

        if (latestConsent.getRevokedAt() != null ||
            (latestConsent.getExpiresAt() != null && latestConsent.getExpiresAt().isBefore(Instant.now()))) {
            return ExchangeResponseDTO.builder()
                .status("NO_ACTIVE_CONSENT")
                .message("Latest consent is revoked or expired.")
                .build();
        }

        if (latestConsent.getConsentToken() == null) {
            return ExchangeResponseDTO.builder()
                .status("NO_CONSENT_TOKEN")
                .message("Consent exists but token not yet generated. Retry.")
                .build();
        }

        // Use strictly the scopes granted in the latest consent, overriding any historical merge
        Set<String> strictGrantedTypes = latestConsent.getGrantedDataTypes();
        String consentToken = latestConsent.getConsentToken();

        Long auditId = auditService.logPending(
            request.getPatientId(),
            request.getHip(),
            request.getHiu(),
            requesterId,
            securityContextHelper.extractHospitalId(),
            latestConsent.getId(),
            0,
            strictGrantedTypes.toString(),
            "PULL"
        );

        try {
            String fhirBundle = hipFhirClient.pullBundle(
                request.getHip(), request.getPatientId(), consentToken, strictGrantedTypes);

            auditService.markSuccess(auditId, fhirBundle, countBundleResources(fhirBundle));
            System.out.println("✅ HIE Gateway: Successfully pulled FHIR bundle. Length: " + fhirBundle.length());

            return ExchangeResponseDTO.builder()
                .status("SUCCESS")
                .consentToken(consentToken)
                .fhirBundle(fhirBundle)
                .message("Data exchange complete.")
                .build();

        } catch (Exception e) {
            auditService.markFailed(auditId, e.getMessage());
            throw e;
        }
    }

    private String resolveHipForCurrentRequester() {
        String hospitalId = securityContextHelper.extractHospitalId();
        return switch (hospitalId) {
            case "HOSP-A" -> "HospitalB";
            case "HOSP-B" -> "HospitalA";
            default -> "HospitalA";
        };
    }

    private String resolveHiuForCurrentRequester() {
        String hospitalId = securityContextHelper.extractHospitalId();
        return switch (hospitalId) {
            case "HOSP-A" -> "HospitalA";
            case "HOSP-B" -> "HospitalB";
            default -> "HospitalA";
        };
    }

    private String resolveHipForPatient(String abhaId, String preferredHip) {
        boolean hasHospitalAConsult = hospitalAOPConsultRepository.findFirstByAbhaIdOrderByIdDesc(abhaId).isPresent();
        boolean hasHospitalBConsult = hospitalBOPConsultRepository.findFirstByAbhaIdOrderByReceivedAtDesc(abhaId).isPresent();

        if (preferredHip != null && !preferredHip.isBlank()) {
            if ("HospitalA".equalsIgnoreCase(preferredHip) && hasHospitalAConsult) {
                return "HospitalA";
            }
            if ("HospitalB".equalsIgnoreCase(preferredHip) && hasHospitalBConsult) {
                return "HospitalB";
            }
        }

        String requesterHospital = resolveHiuForCurrentRequester();
        if (hasHospitalAConsult && !"HospitalA".equalsIgnoreCase(requesterHospital)) {
            return "HospitalA";
        }
        if (hasHospitalBConsult && !"HospitalB".equalsIgnoreCase(requesterHospital)) {
            return "HospitalB";
        }
        if (hasHospitalAConsult) {
            return "HospitalA";
        }
        if (hasHospitalBConsult) {
            return "HospitalB";
        }

        return preferredHip != null && !preferredHip.isBlank() ? preferredHip : resolveHipForCurrentRequester();
    }

    private String resolveHospitalIdFromHip(String hip) {
        if ("HospitalA".equalsIgnoreCase(hip)) {
            return "HOSP-A";
        }
        if ("HospitalB".equalsIgnoreCase(hip)) {
            return "HOSP-B";
        }
        return null;
    }

    private String resolveHospitalIdFromHiu(String hiu) {
        if ("HospitalA".equalsIgnoreCase(hiu)) {
            return "HOSP-A";
        }
        if ("HospitalB".equalsIgnoreCase(hiu)) {
            return "HOSP-B";
        }
        return securityContextHelper.extractHospitalId();
    }

    private int countBundleResources(String fhirBundleJson) {
        try {
            org.hl7.fhir.r4.model.Bundle bundle = ca.uhn.fhir.context.FhirContext.forR4()
                .newJsonParser()
                .parseResource(org.hl7.fhir.r4.model.Bundle.class, fhirBundleJson);
            return bundle.getEntry().size();
        } catch (Exception e) {
            return 0;
        }
    }
}
