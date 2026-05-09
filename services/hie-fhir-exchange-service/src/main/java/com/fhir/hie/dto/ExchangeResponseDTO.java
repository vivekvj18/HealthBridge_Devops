package com.fhir.hie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeResponseDTO {
    private String status;
    private Long consentRequestId;
    private String consentToken;
    private String fhirBundle;
    private String message;
}
