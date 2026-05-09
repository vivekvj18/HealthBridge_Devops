package com.fhir.notification;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    /**
     * Notifies a patient that a consent request is awaiting their approval.
     * MVP: logs to console. Phase 2: replace body with SMS/email call.
     */
    public void notifyPatientConsentRequest(String patientId, Long consentRequestId) {
        System.out.printf(
            "[NOTIFICATION] Patient %s has a pending consent request #%d. " +
            "Please log in and visit /consent/pending/%s to approve or deny.%n",
            patientId, consentRequestId, patientId
        );
    }
}
