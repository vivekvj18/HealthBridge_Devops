package com.fhir.auth.dto;

import com.fhir.auth.model.UserRole;
import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private UserRole role;
    private String abhaId;
    private String email;
    private String phone;
    private String gender;
    private String dateOfBirth;
    private String bloodGroup;
    private String hospitalId;
    private String fullName;
    private String specialization;
}
