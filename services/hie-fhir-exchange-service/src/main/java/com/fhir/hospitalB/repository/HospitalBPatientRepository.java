package com.fhir.hospitalB.repository;

import com.fhir.hospitalB.model.HospitalBPatient;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HospitalBPatientRepository extends MongoRepository<HospitalBPatient, String> {
    Optional<HospitalBPatient> findByAbhaId(String abhaId);
    Optional<HospitalBPatient> findByPatientId(String patientId);
}
