package com.fhir.identity.dto;

import lombok.Data;

@Data
public class RegisterPatientDTO {
    private String abhaId;
    private String hospitalId;
    private String localPatientId;
    private String hospitalAId;
    private String hospitalBId;
    private String name;
    private String dateOfBirth;
    private String gender;
    private String phone;
    private String email;
    private String bloodGroup;
}
