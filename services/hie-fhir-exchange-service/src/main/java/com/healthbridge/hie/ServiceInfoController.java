package com.healthbridge.hie;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceInfoController {

    @GetMapping("/internal/service-info")
    public Map<String, String> serviceInfo() {
        return Map.of("service", "hie-fhir-exchange-service", "phase", "phase-2-skeleton");
    }
}
