package com.fhir.hospitalB.controller;

import com.fhir.hospitalB.dto.HospitalBOPConsultRecordDTO;
import com.fhir.hospitalB.dto.PatientPushRequestBDTO;
import com.fhir.hospitalB.service.HospitalBService;
import com.fhir.notification.PatientPushNotification;
import com.fhir.shared.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * HTTP adapter for Hospital B endpoints.
 * <p>
 * Responsibilities: accept HTTP requests and delegate to
 * {@link HospitalBService}.  No parsing or mapping logic lives here.
 */
@RestController
@RequestMapping("/hospitalB")
public class HospitalBController {

    @Autowired
    private HospitalBService hospitalBService;

    @Autowired
    private SecurityContextHelper securityContextHelper;

    @PostMapping("/op-consult")
    public HospitalBOPConsultRecordDTO receiveFhirBundle(@RequestBody String fhirJson) {
        return hospitalBService.receiveFhirBundle(fhirJson);
    }

    @PostMapping("/op-consult/native")
    public String createNativeConsult(@RequestBody HospitalBOPConsultRecordDTO consultRecord) {
        return hospitalBService.processNativeConsult(consultRecord);
    }

    @GetMapping("/op-consult")
    public java.util.List<com.fhir.hospitalB.model.HospitalBOPConsultEntity> getAllConsults() {
        return hospitalBService.getAllConsults();
    }

    // ── Patient-Initiated Push Flow ───────────────────────────────────────

    @PostMapping("/op-consult/push")
    public String pushOPConsult(@RequestBody PatientPushRequestBDTO pushRequest) {
        if (!securityContextHelper.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated request");
        }
        String patientAbhaId = securityContextHelper.extractAbhaId();
        return hospitalBService.pushOPConsult(pushRequest, patientAbhaId);
    }

    // ── Doctor Inbound Notifications ───────────────────────────────────────

    @GetMapping("/notifications")
    public List<PatientPushNotification> getNotifications() {
        String doctorUsername = securityContextHelper.getCurrentUsername();
        return hospitalBService.getNotificationsForDoctor(doctorUsername);
    }

    @PatchMapping("/notifications/{id}/read")
    public PatientPushNotification markRead(@PathVariable Long id) {
        String doctorUsername = securityContextHelper.getCurrentUsername();
        return hospitalBService.markNotificationRead(id, doctorUsername);
    }
}
