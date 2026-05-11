package com.fhir.hie.service;

import com.fhir.consent.dto.ConsentRequestViewDTO;
import com.fhir.consent.dto.InitiateConsentDTO;
import com.fhir.consent.model.ConsentStatus;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;

@Service
public class HIEGatewayService {

    @Autowired private IdentityService identityService;
    @Autowired private NotificationService notificationService;
    @Autowired private HIPFhirClient hipFhirClient;
    @Autowired private AuditService auditService;
    @Autowired private SecurityContextHelper securityContextHelper;
    @Autowired private HospitalAOPConsultRepository hospitalAOPConsultRepository;
    @Autowired private HospitalBOPConsultRepository hospitalBOPConsultRepository;

    @Value("${consent.service.base-url:http://localhost:9094}")
    private String consentServiceBaseUrl;

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

        // Step 2: Check for existing active GRANTED consent in the consent service
        ConsentRequestViewDTO latestConsent = latestActiveGrantedConsent(
            request.getPatientId(), requesterId);
        Set<String> grantedTypes = grantedTypes(latestConsent);

        if (!grantedTypes.isEmpty()) {
            try {
                // Consent exists — try to pull data
                return pullAndReturn(request, requesterId, latestConsent);
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

        ConsentRequestViewDTO consent = initiateConsentInConsentService(initiateDTO);

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
        ConsentRequestViewDTO consent = getConsentFromConsentService(consentId);

        if (consent.getStatus() == ConsentStatus.GRANTED) {
            ExchangeRequestDTO req = new ExchangeRequestDTO();
            req.setPatientId(consent.getPatientId());
            req.setHip(resolveHipForPatient(consent.getPatientId(), resolveHipForCurrentRequester()));
            req.setHiu(resolveHiuForCurrentRequester());
            req.setScope(consent.getGrantedDataTypes());

            return pullAndReturn(req, consent.getRequesterId(), consent);
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

        ConsentRequestViewDTO consent = initiateConsentInConsentService(initiateDTO);
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

        ConsentRequestViewDTO latestConsent = latestActiveGrantedConsent(request.getPatientId(), requesterId);
        if (latestConsent == null || grantedTypes(latestConsent).isEmpty()) {
            return ExchangeResponseDTO.builder()
                .status("NO_CONSENT")
                .message("No active consent found. Please request consent first.")
                .build();
        }

        request.setHip(resolveHipForPatient(request.getPatientId(), request.getHip()));
        request.setHiu(resolveHiuForCurrentRequester());
        return pullAndReturn(request, requesterId, latestConsent);
    }

    private ExchangeResponseDTO pullAndReturn(
            ExchangeRequestDTO request,
            String requesterId,
            ConsentRequestViewDTO latestConsent) {

        request.setHip(resolveHipForPatient(request.getPatientId(), request.getHip()));
        if (request.getHiu() == null || request.getHiu().isBlank()) {
            request.setHiu(resolveHiuForCurrentRequester());
        }

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

        // Use strictly the scopes granted in this consent, avoiding historical merges.
        Set<String> strictGrantedTypes = grantedTypes(latestConsent);
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

    private ConsentRequestViewDTO initiateConsentInConsentService(InitiateConsentDTO dto) {
        try {
            String authorization = currentAuthorizationHeader();
            return consentClient()
                .post()
                .uri("/consent/initiate")
                .headers(headers -> setAuthorization(headers, authorization))
                .body(dto)
                .retrieve()
                .body(ConsentRequestViewDTO.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to create consent request in consent-service: " + ex.getMessage(), ex);
        }
    }

    private ConsentRequestViewDTO getConsentFromConsentService(Long consentId) {
        try {
            String authorization = currentAuthorizationHeader();
            return consentClient()
                .get()
                .uri("/consent/{requestId}", consentId)
                .headers(headers -> setAuthorization(headers, authorization))
                .retrieve()
                .body(ConsentRequestViewDTO.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to read consent request from consent-service: " + ex.getMessage(), ex);
        }
    }

    private List<ConsentRequestViewDTO> getPatientConsentsFromConsentService(String patientId) {
        try {
            String authorization = currentAuthorizationHeader();
            List<ConsentRequestViewDTO> consents = consentClient()
                .get()
                .uri("/consent/pending/{patientId}", patientId)
                .headers(headers -> setAuthorization(headers, authorization))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            return consents != null ? consents : List.of();
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to list patient consents from consent-service: " + ex.getMessage(), ex);
        }
    }

    private ConsentRequestViewDTO latestActiveGrantedConsent(String patientId, String requesterId) {
        Instant now = Instant.now();
        return getPatientConsentsFromConsentService(patientId).stream()
            .filter(consent -> requesterId.equals(consent.getRequesterId()))
            .filter(consent -> consent.getStatus() == ConsentStatus.GRANTED)
            .filter(consent -> consent.getRevokedAt() == null)
            .filter(consent -> consent.getExpiresAt() == null || consent.getExpiresAt().isAfter(now))
            .max(Comparator.comparing(ConsentRequestViewDTO::getId))
            .orElse(null);
    }

    private Set<String> grantedTypes(ConsentRequestViewDTO consent) {
        if (consent == null || consent.getGrantedDataTypes() == null) {
            return Set.of();
        }
        return new HashSet<>(consent.getGrantedDataTypes());
    }

    private RestClient consentClient() {
        return RestClient.create(consentServiceBaseUrl);
    }

    private void setAuthorization(HttpHeaders headers, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private String currentAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
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
