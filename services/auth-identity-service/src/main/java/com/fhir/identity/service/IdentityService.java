package com.fhir.identity.service;

import com.fhir.identity.dto.RegisterPatientDTO;
import com.fhir.identity.model.HospitalPatientLink;
import com.fhir.identity.model.PatientProfile;
import com.fhir.identity.repository.HospitalPatientLinkRepository;
import com.fhir.identity.repository.PatientProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private HospitalPatientLinkRepository hospitalPatientLinkRepository;

    public PatientProfile register(RegisterPatientDTO dto) {
        if (dto.getAbhaId() == null || dto.getAbhaId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ABHA-ID is required");
        }

        PatientProfile patient = patientProfileRepository.findById(dto.getAbhaId())
            .orElseGet(PatientProfile::new);
        patient.setAbhaId(dto.getAbhaId());
        patient.setFullName(dto.getName());
        patient.setDateOfBirth(dto.getDateOfBirth());
        patient.setGender(dto.getGender());
        patient.setPhone(dto.getPhone());
        patient.setEmail(dto.getEmail());
        patient.setBloodGroup(dto.getBloodGroup());
        PatientProfile saved = patientProfileRepository.save(patient);

        if (hasText(dto.getHospitalId()) && hasText(dto.getLocalPatientId())) {
            linkPatient(dto.getAbhaId(), dto.getHospitalId(), dto.getLocalPatientId());
        }
        if (hasText(dto.getHospitalAId())) {
            linkPatient(dto.getAbhaId(), "HOSP-A", dto.getHospitalAId());
        }
        if (hasText(dto.getHospitalBId())) {
            linkPatient(dto.getAbhaId(), "HOSP-B", dto.getHospitalBId());
        }

        return saved;
    }

    public String resolveGlobalId(String hospitalAId) {
        return hospitalPatientLinkRepository.findByHospitalIdAndLocalPatientId("HOSP-A", hospitalAId)
            .map(HospitalPatientLink::getAbhaId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Patient not registered in identity service: " + hospitalAId));
    }

    public boolean exists(String abhaId) {
        return patientProfileRepository.existsById(abhaId);
    }

    public HospitalPatientLink linkPatient(String abhaId, String hospitalId, String localPatientId) {
        if (!patientProfileRepository.existsById(abhaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found for ABHA-ID: " + abhaId);
        }
        return hospitalPatientLinkRepository.findByHospitalIdAndAbhaId(hospitalId, abhaId)
            .map(existing -> {
                existing.setLocalPatientId(localPatientId);
                existing.setActive(true);
                return hospitalPatientLinkRepository.save(existing);
            })
            .orElseGet(() -> {
                HospitalPatientLink link = new HospitalPatientLink();
                link.setAbhaId(abhaId);
                link.setHospitalId(hospitalId);
                link.setLocalPatientId(localPatientId);
                link.setActive(true);
                return hospitalPatientLinkRepository.save(link);
            });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
