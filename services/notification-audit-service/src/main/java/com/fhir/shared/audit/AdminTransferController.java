package com.fhir.shared.audit;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminTransferController {

    private final TransferAuditLogRepository transferAuditLogRepository;

    public AdminTransferController(TransferAuditLogRepository transferAuditLogRepository) {
        this.transferAuditLogRepository = transferAuditLogRepository;
    }

    @GetMapping("/transfers")
    public List<TransferAuditLog> getAllTransfers() {
        return transferAuditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
    }
}
