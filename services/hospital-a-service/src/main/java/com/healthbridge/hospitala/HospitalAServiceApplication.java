package com.healthbridge.hospitala;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.healthbridge.hospitala", "com.fhir"})
public class HospitalAServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalAServiceApplication.class, args);
    }
}
