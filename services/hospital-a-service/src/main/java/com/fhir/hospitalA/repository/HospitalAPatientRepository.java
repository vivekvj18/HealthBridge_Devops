package com.fhir.hospitalA.repository;

import com.fhir.hospitalA.model.HospitalAPatient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HospitalAPatientRepository extends JpaRepository<HospitalAPatient, String> {
    Optional<HospitalAPatient> findByAbhaId(String abhaId);
}
