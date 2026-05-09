package com.fhir.admin.controller;

import com.fhir.admin.service.AdminService;
import com.fhir.shared.audit.TransferAuditLog;
import com.fhir.auth.dto.RegisterRequest;
import com.fhir.auth.model.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/transfers")
    public List<TransferAuditLog> getAllTransfers() {
        return adminService.getAllTransfers();
    }

    @GetMapping("/audit-logs")
    public List<Map<String, Object>> getAuditLogs() {
        return adminService.getAuditLogs();
    }

    @GetMapping("/system-health")
    public List<Map<String, Object>> getSystemHealth() {
        return adminService.getSystemHealth();
    }

    @GetMapping("/users")
    public List<AppUser> getAllUsers() {
        return adminService.getAllUsers();
    }

    @PostMapping("/users")
    public AppUser createUser(@RequestBody RegisterRequest request) {
        return adminService.createUser(request);
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
    }
}
