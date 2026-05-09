package com.fhir.auth.service;

import com.fhir.auth.dto.LoginRequest;
import com.fhir.auth.dto.LoginResponse;
import com.fhir.auth.dto.RegisterRequest;
import com.fhir.auth.model.AppUser;
import com.fhir.auth.model.UserRole;
import com.fhir.auth.repository.AuthUserRepository;
import com.fhir.identity.model.PatientProfile;
import com.fhir.identity.repository.PatientProfileRepository;
import com.fhir.shared.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Value("${jwt.access-token-expiry-seconds}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry-seconds}")
    private long refreshTokenExpiry;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AppUser register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException(
                    "Username already taken: " + request.getUsername());
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setAbhaId(request.getAbhaId());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setBloodGroup(request.getBloodGroup());
        user.setHospitalId(request.getHospitalId());
        user.setFullName(request.getFullName());
        user.setSpecialization(request.getSpecialization());

        AppUser saved = userRepository.save(user);
        if (saved.getRole() == UserRole.PATIENT && saved.getAbhaId() != null && !saved.getAbhaId().isBlank()) {
            upsertPatientProfile(request);
        }
        return saved;
    }

    @Transactional
    public Map<String, String> registerUser(RegisterRequest request) {
        AppUser saved = register(request);
        return Map.of(
                "message", "User registered successfully",
                "username", saved.getUsername(),
                "role", saved.getRole().name()
        );
    }

    @Transactional(readOnly = true)
    public AppUser findByAbhaId(String abhaId) {
        return userRepository.findByAbhaId(abhaId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getDoctorsByHospital(String hospitalId) {
        return userRepository.findByRoleAndHospitalId(UserRole.DOCTOR, hospitalId)
                .stream()
                .map(doc -> Map.of(
                        "username", doc.getUsername(),
                        "fullName", doc.getFullName() != null ? doc.getFullName() : doc.getUsername(),
                        "specialization", doc.getSpecialization() != null ? doc.getSpecialization() : "General"
                ))
                .toList();
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String accessToken  = buildAccessToken(user);
        String refreshToken = buildAndStoreRefreshToken(user);

        return new LoginResponse(accessToken, refreshToken,
                                 user.getUsername(), user.getRole());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional
    public String refresh(String rawRefreshToken) {
        var claims = jwtUtil.parse(rawRefreshToken);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new IllegalArgumentException("Not a refresh token");
        }

        String username = claims.getSubject();
        String jti = claims.get("jti", String.class); // ✅ extract jti

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // ✅ FIX: compare against jti hash, not raw token
        if (user.getRefreshTokenHash() == null
                || !passwordEncoder.matches(jti, user.getRefreshTokenHash())) {
            throw new IllegalArgumentException("Refresh token has been invalidated");
        }

        return buildAccessToken(user);
    }

    @Transactional
    public Map<String, String> refreshAccessToken(String rawRefreshToken) {
        return Map.of("accessToken", refresh(rawRefreshToken));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildAccessToken(AppUser user) {
        Map<String, Object> claims = Map.of(
                "sub",       user.getUsername(),
                "role",      user.getRole().name(),
                "abhaId",    user.getAbhaId() != null ? user.getAbhaId() : "",
                "hospitalId",user.getHospitalId() != null ? user.getHospitalId() : "",
                "type",      "access"
        );
        return jwtUtil.sign(claims, accessTokenExpiry, user.getUsername());
    }

    private String buildAndStoreRefreshToken(AppUser user) {
        String jti = UUID.randomUUID().toString();
        Map<String, Object> claims = Map.of(
                "sub",  user.getUsername(),
                "role", user.getRole().name(),
                "jti",  jti,
                "type", "refresh"
        );
        String rawToken = jwtUtil.sign(claims, refreshTokenExpiry, user.getUsername());
        // ✅ FIX: hash only the jti (UUID), not the full JWT — BCrypt has a 72-byte limit
        user.setRefreshTokenHash(passwordEncoder.encode(jti));
        userRepository.save(user);
        return rawToken;
    }

    private void upsertPatientProfile(RegisterRequest request) {
        PatientProfile patient = patientProfileRepository.findById(request.getAbhaId())
            .orElseGet(PatientProfile::new);
        patient.setAbhaId(request.getAbhaId());
        patient.setFullName(request.getFullName());
        patient.setEmail(request.getEmail());
        patient.setPhone(request.getPhone());
        patient.setGender(request.getGender());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setBloodGroup(request.getBloodGroup());
        patientProfileRepository.save(patient);
    }
}
