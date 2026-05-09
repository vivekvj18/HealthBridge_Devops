package com.fhir.auth.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "auth_users")
@Data
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /** ABHA-ID is the universal system ID for patients. Stored here only as the login user's patient link. */
    @Column(name = "patient_abha_id", unique = true)
    private String abhaId;

    @Column
    private String email;

    @Column
    private String phone;

    @Column
    private String gender;

    @Column
    private String dateOfBirth;

    @Column
    private String bloodGroup;

    @Column
    private String hospitalId;

    @Column
    private String fullName;

    @Column
    private String specialization;

    /**
     * Bcrypt hash of the last issued refresh token.
     * Cleared on logout; replaced on every /auth/refresh call.
     * Null means no active session.
     */
    @Column
    private String refreshTokenHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
