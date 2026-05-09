package com.fhir.hospitalA.controller;

import com.fhir.hospitalA.dto.HospitalAOPConsultRecordDTO;
import com.fhir.hospitalA.dto.PatientPushRequestDTO;
import com.fhir.hospitalA.service.HospitalAService;
import com.fhir.notification.PatientPushNotification;
import com.fhir.shared.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * HTTP adapter for Hospital A endpoints.
 * <p>
 * Responsibilities: parse HTTP requests, extract authentication context, and
 * delegate to {@link HospitalAService}.  No business logic lives here.
 */
@RestController
@RequestMapping("/hospitalA")
public class HospitalAController {

    @Autowired
    private HospitalAService hospitalAService;

    @Autowired
    private SecurityContextHelper securityContextHelper;

    @PostMapping("/op-consult")
    public String receiveOPConsult(@RequestBody HospitalAOPConsultRecordDTO consultRecord) {
        String requesterId = securityContextHelper.getCurrentUsername();
        return hospitalAService.processOPConsult(consultRecord, requesterId);
    }

    @PostMapping("/op-consult/receive")
    public HospitalAOPConsultRecordDTO receiveFhirBundle(@RequestBody String fhirJson) {
        return hospitalAService.receiveFhirBundle(fhirJson);
    }

    // ── Patient-Initiated Push Flow ──────────────────────────────────────────

    @PostMapping("/op-consult/push")
    public String pushOPConsult(@RequestBody PatientPushRequestDTO pushRequest) {
        if (!securityContextHelper.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated request");
        }
        // Extract ABHA-ID from the JWT claim.
        String patientId = securityContextHelper.extractAbhaId();
        return hospitalAService.pushOPConsult(pushRequest, patientId);
    }

    // ── Doctor Inbound Notifications ────────────────────────────────────────

    /**
     * Returns all patient-push notifications for the authenticated doctor,
     * newest first.
     */
    @GetMapping("/notifications")
    public List<PatientPushNotification> getNotifications() {
        String doctorUsername = securityContextHelper.getCurrentUsername();
        return hospitalAService.getNotificationsForDoctor(doctorUsername);
    }

    /**
     * Marks a specific notification as read.
     */
    @PatchMapping("/notifications/{id}/read")
    public PatientPushNotification markRead(@PathVariable Long id) {
        String doctorUsername = securityContextHelper.getCurrentUsername();
        return hospitalAService.markNotificationRead(id, doctorUsername);
    }
}
