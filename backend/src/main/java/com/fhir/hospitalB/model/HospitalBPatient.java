package com.fhir.hospitalB.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "hospital_b_patients")
@Data
@NoArgsConstructor
public class HospitalBPatient {
    
    @Id
    private String id;

    @Indexed
    private String abhaId;      // Universal ID (e.g. ABHA-1234)

    @Indexed(unique = true)
    private String patientId;   // Local Hospital B ID
    private String fullName;
    private String dateOfBirth;
    private String gender;
    private Instant createdAt;
    private Instant updatedAt;
}
