# Phase 2: Microservice Skeletons

Phase 2 adds empty but runnable Spring Boot service applications without moving business logic out of the current backend yet.

## Added modules

| Module | Default port | Purpose |
| --- | ---: | --- |
| `services/discovery-server` | `8761` | Eureka service registry |
| `services/api-gateway-service` | `8080` | Frontend-facing Gateway route preservation |
| `services/auth-identity-service` | `8081` | Future auth, registration, patient identity, hospital registry owner |
| `services/consent-service` | `8082` | Future consent lifecycle and token owner |
| `services/hospital-a-service` | `8083` | Future Hospital A native clinical record owner |
| `services/hospital-b-service` | `8084` | Future Hospital B Mongo-backed clinical record owner |
| `services/hie-fhir-exchange-service` | `8086` | Future HIE orchestration and FHIR validation owner |
| `services/notification-audit-service` | `8087` | Future notification and exchange audit owner |
| `services/admin-reporting-service` | `8088` | Future admin aggregation owner |

The existing monolith remains as `backend` on its current default port `8085`.

## Build

Run the whole reactor from the repository root:

```bash
./backend/mvnw -f pom.xml -DskipTests compile
```

## Service verification

Each domain service exposes:

- `/actuator/health`
- `/actuator/info`
- `/internal/service-info`

The Discovery Server exposes Eureka on `http://localhost:8761`, and the Gateway preserves the Phase 1 frontend-facing route groups using service-discovery `lb://` targets.

## Transition note

No controllers, repositories, entities, or business workflows have been extracted from the monolith in this phase. That work belongs to Phase 3 and Phase 4 so the frontend contract can stay stable while service ownership changes behind the Gateway.
