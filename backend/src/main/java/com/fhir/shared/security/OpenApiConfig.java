package com.fhir.shared.security;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste your access token from POST /auth/login"
)
public class OpenApiConfig {

    /**
     * Applies the bearerAuth security requirement globally to every endpoint.
     * Public endpoints (register, login, refresh, swagger) are still accessible
     * without a token — this just makes the lock icon appear on all routes in
     * Swagger UI, and the "Authorize" button active at the top.
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}