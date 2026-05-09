package com.fhir.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "hospital_patient_links",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_hospital_local_patient", columnNames = {"hospital_id", "local_patient_id"}),
        @UniqueConstraint(name = "uk_hospital_abha", columnNames = {"hospital_id", "abha_id"})
    }
)
@Data
@NoArgsConstructor
public class HospitalPatientLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "abha_id", nullable = false)
    private String abhaId;

    @Column(name = "hospital_id", nullable = false)
    private String hospitalId;

    @Column(name = "local_patient_id", nullable = false)
    private String localPatientId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
