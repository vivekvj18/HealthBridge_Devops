package com.fhir.notification;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persists every patient-initiated push event so that the target doctor
 * can see inbound records as notifications in their dashboard.
 */
@Entity
@Table(name = "patient_push_notifications")
@Data
@NoArgsConstructor
public class PatientPushNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String exchangeId;

    /** ABHA-ID of the patient who pushed their records. */
    @Column(nullable = false)
    private String patientAbhaId;

    /** Display name of the patient (first + last name from the consult). */
    private String patientName;

    /** Username of the doctor / requester this push was targeted at. */
    @Column(nullable = false)
    private String targetDoctorUsername;

    private String sourceHospitalId;
    private String targetHospitalId;

    /**
     * Hospital code where the push originated — {@code "HOSP-A"} or {@code "HOSP-B"}.
     * Used to scope notification queries per hospital portal.
     */
    @Column(nullable = false)
    private String hospitalCode;

    /** Comma-separated data-type grants, e.g. {@code "OP_CONSULT,LAB_RESULT"}. */
    private String dataTypes;

    private String fhirBundleHash;

    /** The full FHIR Bundle JSON that was pushed. */
    @Column(columnDefinition = "TEXT")
    private String fhirBundleJson;

    /** When the push occurred. */
    @Column(nullable = false, updatable = false)
    private Instant pushedAt;

    /** False until the doctor explicitly marks this notification as read. */
    @Column(nullable = false)
    private boolean isRead = false;

    private Instant readAt;

    @PrePersist
    public void prePersist() {
        this.pushedAt = Instant.now();
    }
}
