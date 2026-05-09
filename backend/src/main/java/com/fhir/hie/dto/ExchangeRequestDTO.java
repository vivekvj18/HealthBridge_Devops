package com.fhir.hie.dto;

import lombok.Data;
import java.util.Set;

@Data
public class ExchangeRequestDTO {
    private String patientId;
    private String hip;
    private String hiu;
    private Set<String> scope;
    private String purpose;
}
