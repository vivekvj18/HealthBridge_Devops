package com.fhir.doctor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorPatientLookupResponseDTO {
    private String patientId;
    private String abhaId;
    private String fullName;
    private String dateOfBirth;
    private String gender;
    private String source;
}
