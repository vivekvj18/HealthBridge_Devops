package com.fhir.consent.repository;

import com.fhir.consent.model.ConsentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentAuditLogRepository extends JpaRepository<ConsentAuditLog, Long> {
    List<ConsentAuditLog> findByPatientIdOrderByTimestampDesc(String patientId);
}
