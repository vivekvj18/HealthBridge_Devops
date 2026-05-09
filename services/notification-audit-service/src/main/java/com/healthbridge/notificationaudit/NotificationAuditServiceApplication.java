package com.healthbridge.notificationaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.healthbridge.notificationaudit", "com.fhir"})
public class NotificationAuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationAuditServiceApplication.class, args);
    }
}
