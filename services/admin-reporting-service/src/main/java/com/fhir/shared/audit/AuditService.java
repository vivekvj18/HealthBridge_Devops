package com.fhir.shared.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuditService {

    @Autowired
    private TransferAuditLogRepository repository;

    /** Insert a PENDING transfer row. Returns the id for later update. */
    @Transactional
    public Long logPending(String patientId,
                           String sourceHospital,
                           String targetHospital,
                           int bundleResourceCount,
                           String consentSnapshot) {
        TransferAuditLog log = new TransferAuditLog();
        log.setPatientId(patientId);
        log.setSourceHospital(sourceHospital);
        log.setTargetHospital(targetHospital);
        log.setBundleResourceCount(bundleResourceCount);
        log.setConsentSnapshot(consentSnapshot);
        log.setExchangeType("PULL");
        log.setStatus(TransferStatus.PENDING);
        return repository.save(log).getId();
    }

    @Transactional
    public Long logPending(String patientId,
                           String sourceHospital,
                           String targetHospital,
                           String requesterUsername,
                           String requesterHospitalId,
                           Long consentRequestId,
                           int bundleResourceCount,
                           String consentSnapshot,
                           String exchangeType) {
        TransferAuditLog log = new TransferAuditLog();
        log.setPatientId(patientId);
        log.setSourceHospital(sourceHospital);
        log.setTargetHospital(targetHospital);
        log.setRequesterUsername(requesterUsername);
        log.setRequesterHospitalId(requesterHospitalId);
        log.setConsentRequestId(consentRequestId);
        log.setBundleResourceCount(bundleResourceCount);
        log.setConsentSnapshot(consentSnapshot);
        log.setExchangeType(exchangeType);
        log.setStatus(TransferStatus.PENDING);
        return repository.save(log).getId();
    }

    @Transactional
    public void markSuccess(Long id) {
        repository.findById(id).ifPresent(log -> {
            log.setStatus(TransferStatus.SUCCESS);
            log.setCompletedAt(Instant.now());
            repository.save(log);
        });
    }

    @Transactional
    public void markSuccess(Long id, String fhirBundlePayload, int bundleResourceCount) {
        repository.findById(id).ifPresent(log -> {
            log.setStatus(TransferStatus.SUCCESS);
            log.setCompletedAt(Instant.now());
            log.setFhirBundleHash(hashPayload(fhirBundlePayload));
            log.setBundleResourceCount(bundleResourceCount);
            repository.save(log);
        });
    }

    @Transactional
    public void markFailed(Long id, String reason) {
        repository.findById(id).ifPresent(log -> {
            log.setStatus(TransferStatus.FAILED);
            log.setFailureReason(reason);
            log.setCompletedAt(Instant.now());
            repository.save(log);
        });
    }

    @Transactional(readOnly = true)
    public java.util.List<TransferAuditLog> getPatientAuditLogs(String patientId) {
        return repository.findByPatientIdOrderByTimestampDesc(patientId);
    }

    public String hashPayload(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash payload", e);
        }
    }
}
