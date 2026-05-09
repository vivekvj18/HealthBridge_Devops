package com.fhir.shared.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/patient/audit")
public class PatientAuditController {

    @Autowired
    private AuditService auditService;

    @GetMapping("/{patientId}")
    public List<TransferAuditLog> getLogs(@PathVariable String patientId) {
        return auditService.getPatientAuditLogs(patientId);
    }
}
