package com.fhir.consent.repository;

import com.fhir.consent.model.ConsentRequestEntity;
import com.fhir.consent.model.ConsentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsentRequestRepository extends JpaRepository<ConsentRequestEntity, Long> {
    List<ConsentRequestEntity> findByPatientIdAndStatus(String patientId, ConsentStatus status);
    List<ConsentRequestEntity> findByPatientId(String patientId);
    List<ConsentRequestEntity> findByPatientIdAndRequesterIdAndStatus(String patientId, String requesterId, ConsentStatus status);
    java.util.Optional<ConsentRequestEntity> findFirstByPatientIdAndRequesterIdAndStatusOrderByIdDesc(String patientId, String requesterId, ConsentStatus status);
}
