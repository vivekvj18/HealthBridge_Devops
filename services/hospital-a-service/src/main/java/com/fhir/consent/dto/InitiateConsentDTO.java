package com.fhir.consent.dto;

import lombok.Data;
import java.util.Set;

@Data
public class InitiateConsentDTO {
    private String patientId;
    private String requesterHospitalId;
    private String providerHospitalId;
    private String purpose;
    private Set<String> requestedDataTypes;
}
