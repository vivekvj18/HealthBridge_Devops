package com.fhir.identity.repository;

import com.fhir.identity.model.HospitalPatientLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HospitalPatientLinkRepository extends JpaRepository<HospitalPatientLink, Long> {
    Optional<HospitalPatientLink> findByHospitalIdAndAbhaId(String hospitalId, String abhaId);
    Optional<HospitalPatientLink> findByHospitalIdAndLocalPatientId(String hospitalId, String localPatientId);
}
