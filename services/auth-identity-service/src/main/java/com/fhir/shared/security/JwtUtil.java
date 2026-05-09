package com.fhir.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Pure JWT utility — signs and parses tokens.
 * No HTTP, no Spring Security concepts. Injected as a bean only to read the secret from properties.
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sign a claims map into a compact JWT string.
     *
     * @param claims        key-value pairs to embed (sub, role, patientId, etc.)
     * @param expirySeconds how long the token is valid
     * @return signed compact JWT
     */
    public String sign(Map<String, Object> claims, long expirySeconds, String subject) {
        long nowMs = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + expirySeconds * 1000))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and validate a compact JWT string.
     *
     * @param token the compact JWT
     * @return validated {@link Claims}
     * @throws JwtException if the token is expired, tampered, or otherwise invalid
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
