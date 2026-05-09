package com.fhir.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientConsultationSummaryDTO {
    private String id;
    private String hospitalName;
    private String doctorName;
    private String visitDate;
    private String recordedAt;
    private String clinicalNotes;
    private String bloodPressure;
    private String temperature;
    private boolean prescriptionAvailable;
}
