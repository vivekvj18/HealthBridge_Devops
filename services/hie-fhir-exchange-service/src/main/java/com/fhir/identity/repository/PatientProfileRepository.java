package com.fhir.identity.repository;

import com.fhir.identity.model.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, String> {
}
