package com.fhir.admin.controller;

import com.fhir.admin.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/audit-logs")
    public List<Map<String, Object>> getAuditLogs() {
        return adminService.getAuditLogs();
    }

    @GetMapping("/system-health")
    public List<Map<String, Object>> getSystemHealth() {
        return adminService.getSystemHealth();
    }
}
