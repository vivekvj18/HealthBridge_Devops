package com.fhir.notification;

import com.fhir.shared.security.SecurityContextHelper;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class NotificationAuditController {

    private final PatientPushNotificationRepository notificationRepository;
    private final SecurityContextHelper securityContextHelper;

    public NotificationAuditController(
            PatientPushNotificationRepository notificationRepository,
            SecurityContextHelper securityContextHelper) {
        this.notificationRepository = notificationRepository;
        this.securityContextHelper = securityContextHelper;
    }

    @GetMapping("/hospitalA/notifications")
    public List<PatientPushNotification> getHospitalANotifications() {
        return getNotifications("HOSP-A");
    }

    @GetMapping("/hospitalB/notifications")
    public List<PatientPushNotification> getHospitalBNotifications() {
        return getNotifications("HOSP-B");
    }

    @Transactional
    @PatchMapping("/hospitalA/notifications/{id}/read")
    public PatientPushNotification markHospitalARead(@PathVariable Long id) {
        return markRead(id, "HOSP-A");
    }

    @Transactional
    @PatchMapping("/hospitalB/notifications/{id}/read")
    public PatientPushNotification markHospitalBRead(@PathVariable Long id) {
        return markRead(id, "HOSP-B");
    }

    private List<PatientPushNotification> getNotifications(String hospitalCode) {
        String doctorUsername = securityContextHelper.getCurrentUsername();
        return notificationRepository.findByTargetDoctorUsernameAndHospitalCodeOrderByPushedAtDesc(
                doctorUsername, hospitalCode);
    }

    private PatientPushNotification markRead(Long notificationId, String hospitalCode) {
        String doctorUsername = securityContextHelper.getCurrentUsername();
        PatientPushNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + notificationId));
        if (!doctorUsername.equals(notification.getTargetDoctorUsername())
                || !hospitalCode.equals(notification.getHospitalCode())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Notification does not belong to the authenticated doctor and hospital.");
        }
        notification.setRead(true);
        notification.setReadAt(Instant.now());
        return notificationRepository.save(notification);
    }
}
