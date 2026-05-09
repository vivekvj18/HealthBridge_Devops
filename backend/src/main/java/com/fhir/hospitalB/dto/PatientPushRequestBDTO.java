package com.fhir.hospitalB.dto;

import lombok.Data;
import java.util.Set;

@Data
public class PatientPushRequestBDTO {
    private String targetRequesterId;
    private Set<String> dataTypes;
}
