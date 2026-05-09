package com.fhir.shared.hospital;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "hospitals")
@Data
public class Hospital {
    @Id
    private String id;
    private String name;
    private String code;
    private String location;
    private String contactEmail;
    private boolean active = true;
}
