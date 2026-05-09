package com.fhir.hospitalA.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "hospital_a_op_consults")
@Data
@NoArgsConstructor
public class HospitalAOPConsultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String patientId;

    @Column
    private String abhaId;

    private String patientFirstName;
    private String patientLastName;

    @Column(nullable = false)
    private String doctorName;

    private String visitDate;

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    private double temperature;
    private String bloodPressure;

    @Column(columnDefinition = "TEXT")
    private String prescriptionPdfBase64;

    // ── Provenance / interoperability fields ─────────────────────────────────
    /** The originating hospital code (e.g. "HOSP-A", "HOSP-B"). Null = native record. */
    private String sourceHospital;

    /** The record ID in the source hospital's local DB, if transferred via FHIR. */
    private String sourceRecordId;

    private String sourceExchangeId;

    private String fhirBundleHash;

    /** True when this row was received from another hospital via a FHIR Bundle exchange. */
    @Column(nullable = false)
    private boolean receivedViaFhir = false;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
    
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
