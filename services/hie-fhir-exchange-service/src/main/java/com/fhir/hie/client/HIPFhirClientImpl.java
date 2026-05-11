package com.fhir.hie.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * HIP client used by the HIE service to pull from the real hospital services.
 */
@Component
public class HIPFhirClientImpl implements HIPFhirClient {

    @Value("${hospital-a.service.base-url:http://localhost:9097}")
    private String hospitalAServiceBaseUrl;

    @Value("${hospital-b.service.base-url:http://localhost:9098}")
    private String hospitalBServiceBaseUrl;

    public String pullBundle(String hip, String patientId, String consentToken, Set<String> scope) {
        if ("HospitalA".equalsIgnoreCase(hip)) {
            return pullFromHospital(hospitalAServiceBaseUrl, "/internal/hospitalA/fhir-bundle", patientId, consentToken, scope);
        }
        if ("HospitalB".equalsIgnoreCase(hip)) {
            return pullFromHospital(hospitalBServiceBaseUrl, "/internal/hospitalB/fhir-bundle", patientId, consentToken, scope);
        }
        throw new IllegalArgumentException("Unknown HIP: " + hip);
    }

    private String pullFromHospital(
            String baseUrl,
            String path,
            String patientId,
            String consentToken,
            Set<String> scope) {
        try {
            return RestClient.create(baseUrl)
                    .post()
                    .uri(path)
                    .body(new PullBundleRequest(patientId, consentToken, scope))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), extractErrorMessage(ex), ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to pull FHIR bundle from hospital service: " + ex.getMessage(), ex);
        }
    }

    private String extractErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null && !body.isBlank() ? body : ex.getMessage();
    }

    private record PullBundleRequest(String patientId, String consentToken, Set<String> scope) {
    }
}
