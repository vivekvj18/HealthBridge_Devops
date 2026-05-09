package com.fhir.doctor.service;

import com.fhir.auth.model.AppUser;
import com.fhir.auth.model.UserRole;
import com.fhir.auth.service.AuthService;
import com.fhir.doctor.dto.DoctorPatientLookupResponseDTO;
import com.fhir.hospitalA.model.HospitalAPatient;
import com.fhir.hospitalA.repository.HospitalAPatientRepository;
import com.fhir.hospitalB.model.HospitalBPatient;
import com.fhir.hospitalB.repository.HospitalBPatientRepository;
import com.fhir.identity.dto.RegisterPatientDTO;
import com.fhir.identity.service.IdentityService;
import com.fhir.shared.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DoctorPatientService {

    private static final String HOSPITAL_A_ID = "HOSP-A";
    private static final String HOSPITAL_B_ID = "HOSP-B";
    private static final String HOSPITAL_A_PATIENT_PREFIX = "HA-P-";
    private static final String HOSPITAL_B_PATIENT_PREFIX = "HB-P-";

    @Autowired
    private AuthService authService;

    @Autowired
    private HospitalAPatientRepository hospitalAPatientRepository;

    @Autowired
    private HospitalBPatientRepository hospitalBPatientRepository;

    @Autowired
    private SecurityContextHelper securityContextHelper;

    @Autowired
    private IdentityService identityService;

    @Value("${auth.identity.base-url:http://localhost:8081}")
    private String authIdentityBaseUrl;

    @Transactional(readOnly = true)
    public DoctorPatientLookupResponseDTO lookupPatient(String identifier) {
        String doctorHospitalId = requireDoctorHospitalId();

        DoctorPatientLookupResponseDTO localMatch = findLocalPatient(identifier, doctorHospitalId);
        if (localMatch != null) {
            return localMatch;
        }

        AppUser globalUser = authService.findByAbhaId(identifier);
        if (globalUser != null && globalUser.getRole() == UserRole.PATIENT) {
            return new DoctorPatientLookupResponseDTO(
                    null,
                    globalUser.getAbhaId(),
                    globalUser.getFullName(),
                    globalUser.getDateOfBirth(),
                    globalUser.getGender(),
                    "GLOBAL"
            );
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found for identifier: " + identifier);
    }

    @Transactional
    public Map<String, String> linkPatientByAbhaId(String abhaId) {
        String doctorHospitalId = requireDoctorHospitalId();

        DoctorPatientLookupResponseDTO existingLocalPatient = findLocalPatient(abhaId, doctorHospitalId);
        if (existingLocalPatient != null && "LOCAL".equals(existingLocalPatient.getSource())) {
            return Map.of(
                    "message", "Patient already linked to hospital",
                    "abhaId", abhaId,
                    "localPatientId", existingLocalPatient.getPatientId(),
                    "hospitalId", doctorHospitalId,
                    "fullName", valueOrEmpty(existingLocalPatient.getFullName())
            );
        }

        AppUser user = authService.findByAbhaId(abhaId);
        if (user == null) {
            user = fetchAndRegisterPatientFromAuthIdentity(abhaId);
        }
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient with ABHA-ID not found");
        }

        String generatedPatientId = generateLocalPatientId(doctorHospitalId);
        identityService.linkPatient(abhaId, doctorHospitalId, generatedPatientId);
        saveLocalPatientLink(doctorHospitalId, generatedPatientId, abhaId, user);

        return Map.of(
                "message", "Patient linked to hospital successfully",
                "abhaId", abhaId,
                "localPatientId", generatedPatientId,
                "hospitalId", doctorHospitalId,
                "fullName", valueOrEmpty(user.getFullName())
        );
    }

    private String requireDoctorHospitalId() {
        String doctorHospitalId = securityContextHelper.extractHospitalId();
        if (doctorHospitalId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor is not associated with a hospital");
        }
        return doctorHospitalId;
    }

    private void saveLocalPatientLink(String hospitalId, String localPatientId, String abhaId, AppUser user) {
        if (HOSPITAL_A_ID.equals(hospitalId)) {
            HospitalAPatient clinicalRecord = new HospitalAPatient();
            clinicalRecord.setPatientId(localPatientId);
            clinicalRecord.setAbhaId(abhaId);
            clinicalRecord.setName(user.getFullName());
            clinicalRecord.setDob(user.getDateOfBirth());
            clinicalRecord.setGender(user.getGender());
            hospitalAPatientRepository.save(clinicalRecord);
            return;
        }

        if (HOSPITAL_B_ID.equals(hospitalId)) {
            HospitalBPatient clinicalRecord = new HospitalBPatient();
            clinicalRecord.setPatientId(localPatientId);
            clinicalRecord.setAbhaId(abhaId);
            clinicalRecord.setFullName(user.getFullName());
            clinicalRecord.setDateOfBirth(user.getDateOfBirth());
            clinicalRecord.setGender(user.getGender());
            Instant now = Instant.now();
            clinicalRecord.setCreatedAt(now);
            clinicalRecord.setUpdatedAt(now);
            hospitalBPatientRepository.save(clinicalRecord);
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported hospital: " + hospitalId);
    }

    private AppUser fetchAndRegisterPatientFromAuthIdentity(String abhaId) {
        Map<String, Object> patient = fetchPatientFromAuthIdentity(abhaId);
        if (patient == null || patient.isEmpty()) {
            return null;
        }

        RegisterPatientDTO dto = new RegisterPatientDTO();
        dto.setAbhaId(value(patient, "abhaId"));
        dto.setName(value(patient, "fullName"));
        dto.setEmail(value(patient, "email"));
        dto.setPhone(value(patient, "phone"));
        dto.setGender(value(patient, "gender"));
        dto.setDateOfBirth(value(patient, "dateOfBirth"));
        dto.setBloodGroup(value(patient, "bloodGroup"));
        identityService.register(dto);

        AppUser user = new AppUser();
        user.setAbhaId(dto.getAbhaId());
        user.setUsername(value(patient, "username"));
        user.setRole(UserRole.PATIENT);
        user.setFullName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setGender(dto.getGender());
        user.setDateOfBirth(dto.getDateOfBirth());
        user.setBloodGroup(dto.getBloodGroup());
        return user;
    }

    private Map<String, Object> fetchPatientFromAuthIdentity(String abhaId) {
        try {
            String authorization = currentAuthorizationHeader();
            return RestClient.create(authIdentityBaseUrl)
                    .get()
                    .uri("/auth/register/patient/{abhaId}", abhaId)
                    .headers(headers -> {
                        if (authorization != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, authorization);
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException ex) {
            return null;
        }
    }

    private String currentAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }

    private String value(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value != null ? value.toString() : null;
    }

    private String generateLocalPatientId(String hospitalId) {
        String prefix = switch (hospitalId) {
            case HOSPITAL_A_ID -> HOSPITAL_A_PATIENT_PREFIX;
            case HOSPITAL_B_ID -> HOSPITAL_B_PATIENT_PREFIX;
            default -> "PX-P-";
        };
        String candidate;
        do {
            int sequence = ThreadLocalRandom.current().nextInt(1000, 10000);
            candidate = prefix + sequence;
        } while (isLocalPatientIdTaken(hospitalId, candidate));
        return candidate;
    }

    private boolean isLocalPatientIdTaken(String hospitalId, String localPatientId) {
        if (HOSPITAL_A_ID.equals(hospitalId)) {
            return hospitalAPatientRepository.findById(localPatientId).isPresent();
        }
        if (HOSPITAL_B_ID.equals(hospitalId)) {
            return hospitalBPatientRepository.findByPatientId(localPatientId).isPresent();
        }
        return false;
    }

    private DoctorPatientLookupResponseDTO findLocalPatient(String identifier, String doctorHospitalId) {
        if (HOSPITAL_A_ID.equals(doctorHospitalId)) {
            Optional<HospitalAPatient> patient = hospitalAPatientRepository.findById(identifier);
            if (patient.isEmpty()) {
                patient = hospitalAPatientRepository.findByAbhaId(identifier);
            }
            return patient
                    .map(value -> new DoctorPatientLookupResponseDTO(
                            value.getPatientId(),
                            value.getAbhaId(),
                            value.getName(),
                            value.getDob(),
                            value.getGender(),
                            "LOCAL"
                    ))
                    .orElse(null);
        }

        if (HOSPITAL_B_ID.equals(doctorHospitalId)) {
            Optional<HospitalBPatient> patient = hospitalBPatientRepository.findByPatientId(identifier);
            if (patient.isEmpty()) {
                patient = hospitalBPatientRepository.findByAbhaId(identifier);
            }
            return patient
                    .map(value -> new DoctorPatientLookupResponseDTO(
                            value.getPatientId(),
                            value.getAbhaId(),
                            value.getFullName(),
                            value.getDateOfBirth(),
                            value.getGender(),
                            "LOCAL"
                    ))
                    .orElse(null);
        }

        return null;
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
