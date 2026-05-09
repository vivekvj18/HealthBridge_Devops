package com.fhir.admin.service;

import com.fhir.shared.audit.TransferAuditLog;
import com.fhir.shared.audit.TransferAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminService {

    @Autowired
    private TransferAuditLogRepository auditLogRepository;

    private List<TransferAuditLog> getAllTransfers() {
        return auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
    }

    public List<Map<String, Object>> getAuditLogs() {
        List<TransferAuditLog> transfers = getAllTransfers();
        List<Map<String, Object>> logs = new ArrayList<>();
        
        for (TransferAuditLog t : transfers) {
            Map<String, Object> log = new HashMap<>();
            log.put("id", t.getId());
            log.put("timestamp", t.getTimestamp().toString());
            log.put("user", t.getSourceHospital() + " -> " + t.getTargetHospital());
            log.put("action", "TRANSFER_" + t.getStatus().name());
            log.put("resource", "Patient " + t.getPatientId());
            log.put("status", t.getStatus().name());
            logs.add(log);
        }
        return logs;
    }

    public List<Map<String, Object>> getSystemHealth() {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        Map<LocalDate, Map<String, Object>> buckets = new LinkedHashMap<>();

        for (int offset = 6; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            entry.put("date", day.toString());
            entry.put("transfers", 0L);
            entry.put("failures", 0L);
            buckets.put(day, entry);
        }

        for (TransferAuditLog log : getAllTransfers()) {
            if (log.getTimestamp() == null) {
                continue;
            }

            LocalDate logDate = log.getTimestamp().atZone(zoneId).toLocalDate();
            Map<String, Object> bucket = buckets.get(logDate);
            if (bucket == null) {
                continue;
            }

            bucket.put("transfers", ((Long) bucket.get("transfers")) + 1L);
            if ("FAILED".equals(log.getStatus().name())) {
                bucket.put("failures", ((Long) bucket.get("failures")) + 1L);
            }
        }

        return new ArrayList<>(buckets.values());
    }
}
