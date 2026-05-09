package com.healthbridge.notificationaudit;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceInfoController {

    @GetMapping("/internal/service-info")
    public Map<String, String> serviceInfo() {
        return Map.of("service", "notification-audit-service", "phase", "phase-2-skeleton");
    }
}
