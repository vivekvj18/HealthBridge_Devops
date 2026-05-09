package com.fhir.consent.service;

import com.fhir.consent.dto.ConsentDecisionDTO;
import com.fhir.consent.dto.ConsentRequestViewDTO;
import com.fhir.consent.dto.InitiateConsentDTO;
import com.fhir.consent.model.ConsentAction;
import com.fhir.consent.model.ConsentAuditLog;
import com.fhir.consent.model.ConsentRequestEntity;
import com.fhir.consent.model.ConsentStatus;
import com.fhir.consent.repository.ConsentAuditLogRepository;
import com.fhir.consent.repository.ConsentRequestRepository;
import com.fhir.shared.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConsentStore {

    @Autowired
    private ConsentRequestRepository requestRepository;

    @Autowired
    private ConsentAuditLogRepository auditLogRepository;

    @Autowired
    private SecurityContextHelper securityContextHelper;

    @Autowired
    private ConsentTokenService consentTokenService;

    // ── Public API ───────────────────────────────────────────────────────────

    @Transactional
    public ConsentRequestViewDTO initiateRequest(InitiateConsentDTO dto, String requesterId) {
        ConsentRequestEntity request = new ConsentRequestEntity();
        request.setPatientId(dto.getPatientId());
        request.setRequesterId(requesterId);
        request.setRequesterHospitalId(firstNonBlank(dto.getRequesterHospitalId(), securityContextHelper.extractHospitalId()));
        request.setProviderHospitalId(firstNonBlank(dto.getProviderHospitalId(), resolveOppositeHospital(request.getRequesterHospitalId())));
        request.setPurpose(dto.getPurpose());
        request.setStatus(ConsentStatus.PENDING);
        request.setRequestedDataTypes(dto.getRequestedDataTypes() != null ? new HashSet<>(dto.getRequestedDataTypes()) : new HashSet<>());
        request.setGrantedDataTypes(new HashSet<>()); // nothing granted initially

        ConsentRequestEntity saved = requestRepository.save(request);
        appendAudit(saved, ConsentAction.INITIATED);
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<ConsentRequestViewDTO> getPendingRequests(String patientId) {
        return requestRepository.findByPatientId(patientId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConsentRequestViewDTO getRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    @Transactional
    public ConsentRequestViewDTO processDecision(Long requestId, String patientId, ConsentDecisionDTO dto) {
        ConsentRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (!request.getPatientId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to respond to this request");
        }

        if (request.getStatus() != ConsentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is already processed");
        }

        request.setStatus(dto.getDecision());
        request.setDecidedAt(Instant.now());
        
        if (dto.getDecision() == ConsentStatus.GRANTED && dto.getGrantedDataTypes() != null) {
            // Optional: validate that granted is subset of requested
            Set<String> safeGranted = dto.getGrantedDataTypes().stream()
                    .filter(request.getRequestedDataTypes()::contains)
                    .collect(Collectors.toSet());
            request.setGrantedDataTypes(safeGranted);
        } else {
            request.getGrantedDataTypes().clear();
        }

        ConsentRequestEntity saved = requestRepository.save(request);

        if (dto.getDecision() == ConsentStatus.GRANTED) {
            String token = consentTokenService.generateConsentToken(saved);
            saved.setConsentTokenId(UUID.randomUUID().toString());
            saved.setConsentToken(token);
            saved.setConsentTokenHash(hashToken(token));
            saved.setExpiresAt(Instant.now().plusSeconds(3600));
            requestRepository.save(saved);
        }

        ConsentAction action = dto.getDecision() == ConsentStatus.GRANTED ? ConsentAction.GRANTED : ConsentAction.DENIED;
        appendAudit(saved, action);

        return mapToDTO(saved);
    }

    @Transactional
    public void revoke(Long requestId, String patientId) {
        ConsentRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
                
        if (!request.getPatientId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to revoke this request");
        }

        request.setStatus(ConsentStatus.REVOKED);
        request.setRevokedAt(Instant.now());
        request.getGrantedDataTypes().clear(); // Clearing the granted data upon revocation
        ConsentRequestEntity saved = requestRepository.save(request);
        appendAudit(saved, ConsentAction.REVOKED);
    }

    @Transactional(readOnly = true)
    public Set<String> getActiveGrantedDataTypes(String patientId, String requesterId) {
        List<ConsentRequestEntity> grantedReqs = requestRepository.findByPatientIdAndRequesterIdAndStatus(patientId, requesterId, ConsentStatus.GRANTED);
        Set<String> activeTypes = new HashSet<>();
        Instant now = Instant.now();
        for (ConsentRequestEntity req : grantedReqs) {
            if (req.getRevokedAt() != null) {
                continue;
            }
            if (req.getExpiresAt() != null && req.getExpiresAt().isBefore(now)) {
                continue;
            }
            if (req.getGrantedDataTypes() != null) {
                activeTypes.addAll(req.getGrantedDataTypes());
            }
        }
        return activeTypes;
    }

    @Transactional
    public void autoGrantForPatientPush(String patientId, String targetRequesterId, Set<String> dataTypes) {
        ConsentRequestEntity request = new ConsentRequestEntity();
        request.setPatientId(patientId);
        request.setRequesterId(targetRequesterId);
        request.setRequesterHospitalId(null);
        request.setProviderHospitalId(null);
        request.setPurpose("Patient-Initiated Push Flow");
        request.setStatus(ConsentStatus.GRANTED);
        request.setDecidedAt(Instant.now());
        request.setExpiresAt(Instant.now().plusSeconds(3600));
        request.setRequestedDataTypes(dataTypes != null ? new HashSet<>(dataTypes) : new HashSet<>());
        request.setGrantedDataTypes(dataTypes != null ? new HashSet<>(dataTypes) : new HashSet<>());

        ConsentRequestEntity saved = requestRepository.save(request);
        appendAudit(saved, ConsentAction.GRANTED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void appendAudit(ConsentRequestEntity request, ConsentAction action) {
        ConsentAuditLog log = new ConsentAuditLog();
        log.setPatientId(request.getPatientId());
        log.setAction(action);
        log.setChangedBy(securityContextHelper.getCurrentUsername());
        log.setRequestSnapshot(buildRequestSnapshot(request));
        auditLogRepository.save(log);
    }

    private String buildRequestSnapshot(ConsentRequestEntity req) {
        return String.format(
                "{\"requestId\":%d,\"requesterId\":\"%s\",\"purpose\":\"%s\",\"status\":\"%s\",\"requested\":%s,\"granted\":%s}",
                req.getId(),
                req.getRequesterId(),
                req.getPurpose(),
                req.getStatus(),
                req.getRequestedDataTypes() != null ? req.getRequestedDataTypes().toString() : "[]",
                req.getGrantedDataTypes() != null ? req.getGrantedDataTypes().toString() : "[]"
        );
    }

    private ConsentRequestViewDTO mapToDTO(ConsentRequestEntity entity) {
        ConsentRequestViewDTO dto = new ConsentRequestViewDTO();
        dto.setId(entity.getId());
        dto.setPatientId(entity.getPatientId());
        dto.setRequesterId(entity.getRequesterId());
        dto.setRequesterHospitalId(entity.getRequesterHospitalId());
        dto.setProviderHospitalId(entity.getProviderHospitalId());
        dto.setPurpose(entity.getPurpose());
        dto.setStatus(entity.getStatus());
        dto.setRequestedDataTypes(entity.getRequestedDataTypes() != null ? new HashSet<>(entity.getRequestedDataTypes()) : new HashSet<>());
        dto.setGrantedDataTypes(entity.getGrantedDataTypes() != null ? new HashSet<>(entity.getGrantedDataTypes()) : new HashSet<>());
        dto.setConsentToken(entity.getConsentToken());
        dto.setConsentTokenId(entity.getConsentTokenId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setDecidedAt(entity.getDecidedAt());
        dto.setRevokedAt(entity.getRevokedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        return dto;
    }

    private String resolveOppositeHospital(String hospitalId) {
        if ("HOSP-A".equals(hospitalId)) {
            return "HOSP-B";
        }
        if ("HOSP-B".equals(hospitalId)) {
            return "HOSP-A";
        }
        return null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash consent token", e);
        }
    }
}
