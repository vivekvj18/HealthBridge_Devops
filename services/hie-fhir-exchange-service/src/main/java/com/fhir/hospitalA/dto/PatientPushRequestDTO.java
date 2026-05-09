package com.fhir.hospitalA.dto;

import lombok.Data;
import java.util.Set;

@Data
public class PatientPushRequestDTO {
    private String targetRequesterId;
    private Set<String> dataTypes;
    /**
     * Hospital where the target doctor works (e.g. "HOSP-A" or "HOSP-B").
     * Used to tag the notification so the correct doctor portal can query it.
     * Defaults to "HOSP-A" when absent (backward-compatible).
     */
    private String targetHospitalId;
}
