package com.fhir.hie.client;

import java.util.Set;

public interface HIPFhirClient {
    /**
     * Pulls a filtered FHIR bundle from the HIP for the given patient.
     * @param hip         the target Health Information Provider (e.g. "HospitalA")
     * @param patientId   the ABHA-ID
     * @param consentToken signed JWT consent token issued by ConsentTokenService
     * @param scope       the set of logical data types granted (e.g. "Diagnostics")
     * @return pretty-printed FHIR Bundle JSON string
     */
    String pullBundle(String hip, String patientId, String consentToken, Set<String> scope);
}
