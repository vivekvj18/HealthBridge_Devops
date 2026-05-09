# health-bridge-common

Phase 1 scaffold for the future shared library used by the health-bridge microservices split.

## Intended contents

- shared DTOs that are exchanged across services
- security constants and internal API headers
- shared error response models
- FHIR helper primitives that are not tied to one service's persistence model

## First extraction candidates from the current monolith

- `com.fhir.shared.security.JwtUtil`
- `com.fhir.shared.security.SecurityContextHelper`
- `com.fhir.shared.exception.GlobalExceptionHandler` after adding a stable error model
- `com.fhir.shared.validation.FhirConfig`
- narrowly scoped helper classes around FHIR bundle validation and metadata

## Intentionally not shared

- JPA entities
- Mongo documents
- repositories
- hospital-specific consult DTOs that mirror native persistence models
- service implementations

This module is intentionally lightweight in Phase 1. It gives the repo a stable landing zone before the backend is physically split into microservices.
