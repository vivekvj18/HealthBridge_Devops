package com.healthbridge.adminreporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.healthbridge.adminreporting", "com.fhir"})
public class AdminReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminReportingServiceApplication.class, args);
    }
}
