package com.fhir.shared.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:5173,http://localhost:5174,http://localhost:5175}")
    private String allowedOriginPatterns;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // ── Public endpoints ────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register/patient").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/doctors").permitAll()
                .requestMatchers(HttpMethod.GET, "/hospitals").permitAll()

                // ── Admin endpoints ─────────────────────────────────────────
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/hospitals/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/hospitals/**").hasRole("ADMIN")

                // ── Swagger / OpenAPI ───────────────────────────────────────
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**",
                                 "/swagger-ui.html").permitAll()

                // ── Operational health endpoints ───────────────────────────
                .requestMatchers("/actuator/health", "/actuator/health/**",
                                 "/actuator/info").permitAll()

                // ── Consent endpoints — ADMIN or PATIENT only ───────────────
                .requestMatchers(HttpMethod.POST, "/consent/**")
                    .hasAnyRole("ADMIN", "PATIENT")
                .requestMatchers(HttpMethod.GET, "/consent/pending/**")
                    .hasAnyRole("ADMIN", "PATIENT")

                // ── Hospital A endpoints ─────────────────────────────────────
                // Patient push must be listed before the wildcard rule.
                .requestMatchers(HttpMethod.POST, "/hospitalA/op-consult/push")
                    .hasRole("PATIENT")
                .requestMatchers(HttpMethod.GET, "/hospitalA/notifications")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.PATCH, "/hospitalA/notifications/**")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.POST, "/hospitalA/**")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.GET, "/hospitalA/**")
                    .hasAnyRole("ADMIN", "DOCTOR")

                // ── Doctor endpoints ────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/doctor/patients/lookup/**")
                    .hasRole("DOCTOR")
                .requestMatchers(HttpMethod.POST, "/doctor/patients/link/**")
                    .hasRole("DOCTOR")
                .requestMatchers(HttpMethod.GET, "/auth/register/patient/**")
                    .hasAnyRole("ADMIN", "DOCTOR")

                // ── Hospital B endpoints ─────────────────────────────────────
                // Patient push must be listed before the wildcard rule.
                .requestMatchers(HttpMethod.POST, "/hospitalB/op-consult/push")
                    .hasRole("PATIENT")
                .requestMatchers(HttpMethod.GET, "/hospitalB/notifications")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.PATCH, "/hospitalB/notifications/**")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.POST, "/hospitalB/**")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.GET, "/hospitalB/**")
                    .hasAnyRole("ADMIN", "DOCTOR")

                // ── Identity endpoints — ADMIN only ────────────────────────
                .requestMatchers(HttpMethod.POST, "/identity/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/identity/**")
                    .hasRole("ADMIN")

                // ── HIE Gateway — ADMIN or DOCTOR ──────────────────────────
                .requestMatchers(HttpMethod.POST, "/hie/exchange")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.POST, "/hie/consent-only")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.POST, "/hie/pull-only")
                    .hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers(HttpMethod.GET, "/hie/exchange/**")
                    .hasAnyRole("ADMIN", "DOCTOR")

                // ── Fallback: any other request must be authenticated ───────
                // NOTE: Every new endpoint should be listed above explicitly.
                // This catch-all prevents accidental public exposure.
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> 
                    response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage()))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(parseAllowedOriginPatterns());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseAllowedOriginPatterns() {
        return Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
