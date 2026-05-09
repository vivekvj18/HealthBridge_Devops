package com.fhir.hospitalA.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "hospital_a_patients")
@Data
public class HospitalAPatient {
    @Id
    private String patientId;   // Local hospital ID
    
    @Column(unique = true)
    private String abhaId;      // Universal ID

    private String name;        // Full name
    private String dob;         // dd/MM/yyyy (NON-FHIR)
    private String gender;      // M / F
}
