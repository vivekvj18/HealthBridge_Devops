package com.fhir.consent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

import java.util.Set;
import java.util.HashSet;

@Entity
@Table(name = "consent_requests")
@Data
@NoArgsConstructor
public class ConsentRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String patientId;

    @Column(name = "requester_username", nullable = false)
    private String requesterId;

    private String requesterHospitalId;
    private String providerHospitalId;

    @Column(nullable = false)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "consent_requested_types", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "data_type")
    private Set<String> requestedDataTypes = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "consent_granted_types", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "data_type")
    private Set<String> grantedDataTypes = new HashSet<>();

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Column(columnDefinition = "TEXT")
    private String consentToken;

    private String consentTokenId;

    @Column(columnDefinition = "TEXT")
    private String consentTokenHash;

    private Instant decidedAt;
    private Instant revokedAt;
    private Instant expiresAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
