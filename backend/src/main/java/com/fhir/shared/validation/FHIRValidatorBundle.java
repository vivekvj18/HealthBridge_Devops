package com.fhir.shared.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strict FHIR Bundle validator.
 *
 * <p>Uses a full {@link FhirInstanceValidator} backed by a {@link ValidationSupportChain}
 * (default profiles + in-memory terminology + common code systems).  Any bundle that fails
 * FHIR R4 structural or terminology checks causes a {@link ResponseStatusException} with
 * HTTP 422 to be thrown, preventing invalid data from being persisted.
 *
 * <p>Called by both Hospital A and Hospital B on every inbound receive path and on every
 * outbound push/pull path before the bundle is serialised.
 */
@Component
public class FHIRValidatorBundle {

    @Autowired
    private FhirContext fhirContext;

    /**
     * Validates the given FHIR Bundle against R4 base profiles.
     *
     * @param bundle the bundle to validate
     * @throws ResponseStatusException HTTP 422 if the bundle is structurally invalid
     */
    public void validate(Bundle bundle) {
        FhirValidator validator = fhirContext.newValidator();

        ValidationSupportChain supportChain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(fhirContext),
                new InMemoryTerminologyServerValidationSupport(fhirContext),
                new CommonCodeSystemsTerminologyService(fhirContext)
        );

        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(supportChain);
        validator.registerValidatorModule(instanceValidator);

        ValidationResult result = validator.validateWithResult(bundle);

        if (!result.isSuccessful()) {
            // Collect all error/fatal messages for the response body
            String errors = result.getMessages().stream()
                    .filter(m -> m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR
                            || m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.FATAL)
                    .map(m -> "[" + m.getSeverity() + " @ " + m.getLocationString() + "] " + m.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown FHIR validation failure");

            // Also log warnings for observability
            result.getMessages().stream()
                    .filter(m -> m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.WARNING
                            || m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.INFORMATION)
                    .forEach(m -> System.out.println(
                            "FHIR Validation [" + m.getSeverity() + "]: " + m.getMessage()));

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FHIR Bundle validation failed: " + errors);
        }

        System.out.println("[FHIRValidatorBundle] Bundle passed FHIR R4 validation ✓");
    }
}