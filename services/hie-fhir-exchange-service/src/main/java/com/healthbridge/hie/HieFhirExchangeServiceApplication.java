package com.healthbridge.hie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.healthbridge.hie", "com.fhir"})
public class HieFhirExchangeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HieFhirExchangeServiceApplication.class, args);
    }
}
