package com.healthbridge.authidentity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.healthbridge.authidentity", "com.fhir"})
public class AuthIdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthIdentityServiceApplication.class, args);
    }
}
// Testing Jenkins Selective Build Logic - Viva Demo
// Testing Jenkins Selective Build Logic - Viva Demo


