package com.fhir.hospitalA.dto;

import lombok.Data;

@Data
public class HospitalAOPConsultRecordDTO {
    private String patientId;
    private String abhaId;
    private String patientFirstName;
    private String patientLastName;
    private String doctorName;
    private String visitDate;        // Hospital-A specific format
    private String symptoms;
    private double temperature;
    private String bloodPressure;
    private String prescriptionPdfBase64;
}
