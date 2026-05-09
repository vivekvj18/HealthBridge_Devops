package com.healthbridge.hospitalb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.healthbridge.hospitalb", "com.fhir"})
public class HospitalBServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalBServiceApplication.class, args);
    }
}
