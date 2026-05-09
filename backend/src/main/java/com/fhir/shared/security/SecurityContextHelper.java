package com.fhir.shared.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Centralised helper for extracting identity information from the current
 * Spring Security context.  Eliminates duplicated auth-extraction code that
 * was spread across controllers and services.
 */
@Component
public class SecurityContextHelper {

    /**
     * Returns the username (JWT subject) of the currently authenticated
     * principal, or {@code "system"} when no authentication is present.
     */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "system";
    }

    /**
     * Returns the {@code patientId} claim embedded in the JWT when the
     * currently authenticated principal is a patient.  Falls back to
     * {@link #getCurrentUsername()} when the claim is absent (e.g. for
     * DOCTOR / ADMIN tokens that do not carry a patientId).
     */
    public String extractAbhaId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Claims claims) {
            String abhaId = claims.get("abhaId", String.class);
            if (abhaId != null && !abhaId.isBlank()) {
                return abhaId;
            }
        }
        return getCurrentUsername();
    }

    /**
     * Returns the {@code hospitalId} claim embedded in the JWT.
     */
    public String extractHospitalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Claims claims) {
            String hospitalId = claims.get("hospitalId", String.class);
            if (hospitalId != null && !hospitalId.isBlank()) {
                return hospitalId;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} when a valid, non-anonymous authentication token
     * is present in the security context.
     */
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getName() != null;
    }
}
