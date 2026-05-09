package com.fhir.consent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only audit trail for every consent change.
 * Rows are NEVER updated or deleted — this is the legal audit record.
 */
@Entity
@Table(name = "consent_audit_log")
@Data
@NoArgsConstructor
public class ConsentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentAction action;

    @Column(nullable = false)
    private String changedBy;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    /** JSON snapshot of the ConsentRequest at the moment of this event. */
    @Column(columnDefinition = "TEXT")
    private String requestSnapshot;

    @PrePersist
    public void prePersist() {
        this.timestamp = Instant.now();
    }
}
