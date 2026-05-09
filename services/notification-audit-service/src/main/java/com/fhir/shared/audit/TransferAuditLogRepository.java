package com.fhir.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferAuditLogRepository extends JpaRepository<TransferAuditLog, Long> {
    List<TransferAuditLog> findByPatientIdOrderByTimestampDesc(String patientId);
}
