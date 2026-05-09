package com.fhir.shared.audit;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ehr_exchange_log")
@Data
@NoArgsConstructor
public class TransferAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String exchangeId;

    @Column(nullable = false)
    private String patientId;

    private Long consentRequestId;
    private String requesterUsername;
    private String requesterHospitalId;

    @Column(nullable = false)
    private String sourceHospital;

    @Column(nullable = false)
    private String targetHospital;

    private int bundleResourceCount;
    private String exchangeType;
    private String sourceRecordId;
    private String fhirBundleHash;

    /** JSON snapshot of consent flags at transfer time. */
    @Column(columnDefinition = "TEXT")
    private String consentSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    private Instant completedAt;

    /** Populated only when status = FAILED. */
    @Column
    private String failureReason;

    @PrePersist
    public void prePersist() {
        if (this.exchangeId == null || this.exchangeId.isBlank()) {
            this.exchangeId = java.util.UUID.randomUUID().toString();
        }
        this.timestamp = Instant.now();
    }
}
