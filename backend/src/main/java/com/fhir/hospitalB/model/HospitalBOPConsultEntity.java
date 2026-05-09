package com.fhir.hospitalB.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "hospital_b_op_consults")
@CompoundIndexes({
    @CompoundIndex(name = "idx_abha_received_at", def = "{'abhaId': 1, 'receivedAt': -1}"),
    @CompoundIndex(name = "idx_source_record", def = "{'sourceHospital': 1, 'sourceRecordId': 1}")
})
@Data
@NoArgsConstructor
public class HospitalBOPConsultEntity {

    @Id
    private String id;

    @Indexed
    private String abhaId;
    @Indexed
    private String patientId;
    private String patientName;
    private String consultDate;
    private String doctor;

    private String clinicalNotes;

    private String bloodPressure;
    private String temperature;

    private String prescriptionPdfBase64;

    private boolean consentVerified;

    // ── Provenance / interoperability fields ─────────────────────────────────
    /** The originating hospital code (e.g. "HOSP-A", "HOSP-B"). Null = native record. */
    private String sourceHospital;

    /** The record ID in the source hospital's local DB, if transferred via FHIR. */
    private String sourceRecordId;

    private String sourceExchangeId;

    private String fhirBundleHash;

    /** True when this row was received from another hospital via a FHIR Bundle exchange. */
    private boolean receivedViaFhir = false;

    private Instant receivedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public void stampCreated() {
        Instant now = Instant.now();
        if (this.receivedAt == null) {
            this.receivedAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }
}
