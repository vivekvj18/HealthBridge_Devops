package com.fhir.hie.client;

import com.fhir.hospitalA.service.HospitalAService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * MVP implementation: direct in-JVM call to HospitalAService.
 * In production this becomes a REST call to Hospital A's FHIR endpoint.
 * The interface stays identical — swap only this class.
 */
@Component
public class HIPFhirClientImpl implements HIPFhirClient {

    @Autowired
    private HospitalAService hospitalAService;

    @Autowired
    private com.fhir.hospitalB.service.HospitalBService hospitalBService;

    public String pullBundle(String hip, String patientId, String consentToken, Set<String> scope) {
        if ("HospitalA".equalsIgnoreCase(hip)) {
            return hospitalAService.pullFhirBundle(patientId, consentToken, scope);
        } else if ("HospitalB".equalsIgnoreCase(hip)) {
            return hospitalBService.pullFhirBundle(patientId, consentToken, scope);
        } else {
            throw new IllegalArgumentException("Unknown HIP: " + hip);
        }
    }
}
