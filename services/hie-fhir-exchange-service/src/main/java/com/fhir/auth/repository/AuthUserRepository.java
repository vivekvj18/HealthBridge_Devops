package com.fhir.auth.repository;

import com.fhir.auth.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByAbhaId(String abhaId);
    java.util.List<AppUser> findByRoleAndHospitalId(com.fhir.auth.model.UserRole role, String hospitalId);
}
