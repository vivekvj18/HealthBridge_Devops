package com.fhir.consent.dto;

import com.fhir.consent.model.ConsentStatus;
import lombok.Data;
import java.time.Instant;
import java.util.Set;

@Data
public class ConsentRequestViewDTO {
    private Long id;
    private String patientId;
    private String requesterId;
    private String requesterHospitalId;
    private String providerHospitalId;
    private String purpose;
    private ConsentStatus status;
    private Set<String> requestedDataTypes;
    private Set<String> grantedDataTypes;
    private String consentToken;
    private String consentTokenId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant decidedAt;
    private Instant revokedAt;
    private Instant expiresAt;
}
