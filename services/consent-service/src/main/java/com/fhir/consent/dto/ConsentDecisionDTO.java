package com.fhir.consent.dto;

import com.fhir.consent.model.ConsentStatus;
import lombok.Data;
import java.util.Set;

@Data
public class ConsentDecisionDTO {
    private ConsentStatus decision;
    private Set<String> grantedDataTypes;
}
