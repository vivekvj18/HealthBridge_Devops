package com.fhir.shared.config;

import com.fhir.auth.dto.RegisterRequest;
import com.fhir.auth.model.UserRole;
import com.fhir.auth.repository.AuthUserRepository;
import com.fhir.auth.service.AuthService;
import com.fhir.hospitalA.model.HospitalAOPConsultEntity;
import com.fhir.hospitalA.repository.HospitalAOPConsultRepository;
import com.fhir.hospitalB.model.HospitalBOPConsultEntity;
import com.fhir.hospitalB.model.HospitalBPatient;
import com.fhir.hospitalB.repository.HospitalBOPConsultRepository;
import com.fhir.hospitalB.repository.HospitalBPatientRepository;
import com.fhir.identity.model.HospitalPatientLink;
import com.fhir.identity.repository.HospitalPatientLinkRepository;
import com.fhir.shared.hospital.Hospital;
import com.fhir.shared.hospital.HospitalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DatabaseSeeder implements CommandLineRunner {

    private static final String SAMPLE_ABHA_ID = "ABHA-2233-4455-6677-88";
    private static final String SAMPLE_HOSPITAL_A_PATIENT_ID = "HA-P-1001";
    private static final String SAMPLE_HOSPITAL_B_PATIENT_ID = "HB-P-8002";

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private HospitalAOPConsultRepository consultRepository;

    @Autowired
    private HospitalBOPConsultRepository hospitalBConsultRepository;

    @Autowired
    private HospitalBPatientRepository hospitalBPatientRepository;

    @Autowired
    private HospitalPatientLinkRepository hospitalPatientLinkRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Override
    public void run(String... args) {
        ensureHospitals();
        ensureUsers();
        ensureHospitalASeedConsult();
        ensureHospitalBSeedPatient();
        ensureHospitalBSeedConsult();
        ensureHospitalLinks();
        System.out.println("✅ [DatabaseSeeder] Core relational and Mongo seed data is aligned.");
    }

    private void ensureHospitals() {
        ensureHospital("HOSP-A", "City General Hospital", "CGH-MUM", "Mumbai, Maharashtra", "admin@citygeneral.example");
        ensureHospital("HOSP-B", "Metro Medical Center", "MMC-DEL", "New Delhi, Delhi", "admin@metromedical.example");
    }

    private void ensureHospital(String id, String name, String code, String location, String email) {
        Hospital hospital = hospitalRepository.findById(id).orElseGet(Hospital::new);
        hospital.setId(id);
        hospital.setName(name);
        hospital.setCode(code);
        hospital.setLocation(location);
        hospital.setContactEmail(email);
        hospital.setActive(true);
        hospitalRepository.save(hospital);
    }

    private void ensureUsers() {
        ensureAdminUser();
        ensureDoctorUser("dr_sharma", "doctorpassword", "HOSP-A", "Dr. Rahul Sharma", "Cardiology");
        ensureDoctorUser("dr_gupta", "doctorpassword", "HOSP-B", "Dr. Sneha Gupta", "Neurology");
        ensurePatientUser();
    }

    private void ensureAdminUser() {
        if (userRepository.findByUsername("admin1").isPresent()) {
            return;
        }

        RegisterRequest admin = new RegisterRequest();
        admin.setUsername("admin1");
        admin.setPassword("adminpassword");
        admin.setRole(UserRole.ADMIN);
        admin.setFullName("System Administrator");
        authService.register(admin);
    }

    private void ensureDoctorUser(String username, String password, String hospitalId, String fullName,
                                  String specialization) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        RegisterRequest doctor = new RegisterRequest();
        doctor.setUsername(username);
        doctor.setPassword(password);
        doctor.setRole(UserRole.DOCTOR);
        doctor.setHospitalId(hospitalId);
        doctor.setFullName(fullName);
        doctor.setSpecialization(specialization);
        authService.register(doctor);
    }

    private void ensurePatientUser() {
        if (userRepository.findByUsername("rahul_verma").isPresent()) {
            return;
        }

        RegisterRequest patient = new RegisterRequest();
        patient.setUsername("rahul_verma");
        patient.setPassword("patientpassword");
        patient.setRole(UserRole.PATIENT);
        patient.setAbhaId(SAMPLE_ABHA_ID);
        patient.setHospitalId("HOSP-A");
        patient.setFullName("Rahul Verma");
        authService.register(patient);
    }

    private void ensureHospitalASeedConsult() {
        if (consultRepository.findFirstByAbhaIdOrderByIdDesc(SAMPLE_ABHA_ID).isPresent()) {
            return;
        }

        HospitalAOPConsultEntity consult = new HospitalAOPConsultEntity();
        consult.setPatientId(SAMPLE_HOSPITAL_A_PATIENT_ID);
        consult.setAbhaId(SAMPLE_ABHA_ID);
        consult.setPatientFirstName("Rahul");
        consult.setPatientLastName("Verma");
        consult.setDoctorName("Dr. Rahul Sharma");
        consult.setVisitDate("2026-04-25");
        consult.setSymptoms("Chest pain, slight shortness of breath");
        consult.setTemperature(37.2);
        consult.setBloodPressure("145/90");
        consult.setPrescriptionPdfBase64("JVBERi0xLjQKJcOkw7zDtsOfCjIgMCBvYmoKPDwvTGVuZ3RoIDMgMCBSL0ZpbHRlci9GbGF0ZURlY29kZT4+CnN0cmVhbQp4nDPQM1Qo5ypUMFAwALJMLU31jBQsTAz1DBSKikqTdAwVyitLijLzEnXyC0tS01OLdAtKEvPSkzXyE0sS0/M0cjPz0hLz0tMzy1I1c1I1U7LzU4sLwFKNdY11jQ11TQ30DHRBwoXGhiDxhvqmliA1RhYA1TAn3gplbmRzdHJlYW0KZW5kb2JqCjMgMCBvYmoKOTIKZW5kb2JqCjQgMCBvYmoKPDwvVHlwZS9QYWdlL01lZGlhQm94WzAgMCA1OTUgODQyXS9SZXNvdXJjZXM8PC9Gb250PDwvRjEgMSAwIFI+Pj4+L0NvbnRlbnRzIDIgMCBSL1BhcmVudCA1IDAgUj4+CmVuZG9iago1IDAgb2JqCjw8L1R5cGUvUGFnZXMvS2lkc1s0IDAgUl0vQ291bnQgMT4+CmVuZG9iagoxIDAgb2JqCjw8L1R5cGUvRm9udC9TdWJ0eXBlL1R5cGUxL0Jhc2VGb250L0hlbHZldGljYT4+CmVuZG9iago2IDAgb2JqCjw8L1R5cGUvQ2F0YWxvZy9QYWdlcyA1IDAgUj4+CmVuZG9iagp4cmVmCjAgNwowMDAwMDAwMDAwIDY1NTM1IGYgCjAwMDAwMDAyOTggMDAwMDAgbiAKMDAwMDAwMDAxNSAwMDAwMCBuIAcwMDAwMDAwMTgzIDAwMDAwIG4gCjAwMDAwMDAyMDIgMDAwMDAgbiAKMDAwMDAwMDI0NyAwMDAwMCBuIAcwMDAwMDAwMzY4IDAwMDAwIG4gCnRyYWlsZXIKPDwvU2l6ZSA3L1Jvb3QgNiAwIFI+PgpzdGFydHhyZWYKNDI0CiUlRU9GCg==");
        consult.setReceivedViaFhir(false);
        consult.setSourceHospital("HOSP-A");
        consult.setSourceRecordId(null);
        consultRepository.save(consult);
    }

    private void ensureHospitalBSeedPatient() {
        if (hospitalBPatientRepository.findByPatientId(SAMPLE_HOSPITAL_B_PATIENT_ID).isPresent()) {
            return;
        }

        HospitalBPatient patient = new HospitalBPatient();
        patient.setAbhaId(SAMPLE_ABHA_ID);
        patient.setPatientId(SAMPLE_HOSPITAL_B_PATIENT_ID);
        patient.setFullName("Rahul Verma");
        patient.setDateOfBirth("1992-08-14");
        patient.setGender("Male");
        java.time.Instant now = java.time.Instant.now();
        patient.setCreatedAt(now);
        patient.setUpdatedAt(now);
        hospitalBPatientRepository.save(patient);
    }

    private void ensureHospitalBSeedConsult() {
        if (hospitalBConsultRepository.findFirstByAbhaIdOrderByReceivedAtDesc(SAMPLE_ABHA_ID).isPresent()) {
            return;
        }

        HospitalBOPConsultEntity consult = new HospitalBOPConsultEntity();
        consult.setPatientId(SAMPLE_HOSPITAL_B_PATIENT_ID);
        consult.setAbhaId(SAMPLE_ABHA_ID);
        consult.setPatientName("Rahul Verma");
        consult.setConsultDate("2026-03-15");
        consult.setDoctor("Dr. Sneha Gupta");
        consult.setClinicalNotes("Patient complained of chronic headaches. Prescribed rest and hydration.");
        consult.setTemperature("98.6");
        consult.setBloodPressure("120/80");
        consult.setConsentVerified(true);
        consult.setPrescriptionPdfBase64("");
        consult.setReceivedViaFhir(false);
        consult.setSourceHospital("HOSP-B");
        consult.setSourceRecordId(null);
        consult.stampCreated();
        hospitalBConsultRepository.save(consult);
    }

    private void ensureHospitalLinks() {
        ensureHospitalLink("HOSP-A", SAMPLE_ABHA_ID, SAMPLE_HOSPITAL_A_PATIENT_ID);
        ensureHospitalLink("HOSP-B", SAMPLE_ABHA_ID, SAMPLE_HOSPITAL_B_PATIENT_ID);
    }

    private void ensureHospitalLink(String hospitalId, String abhaId, String localPatientId) {
        if (hospitalPatientLinkRepository.findByHospitalIdAndAbhaId(hospitalId, abhaId).isPresent()) {
            return;
        }

        HospitalPatientLink link = new HospitalPatientLink();
        link.setAbhaId(abhaId);
        link.setHospitalId(hospitalId);
        link.setLocalPatientId(localPatientId);
        hospitalPatientLinkRepository.save(link);
    }
}
