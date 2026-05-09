package com.healthbridge.consent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.healthbridge.consent", "com.fhir"})
public class ConsentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsentServiceApplication.class, args);
    }
}
