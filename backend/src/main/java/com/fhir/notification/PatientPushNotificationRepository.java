package com.fhir.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientPushNotificationRepository extends JpaRepository<PatientPushNotification, Long> {

    /** All notifications for a specific doctor at a specific hospital, newest first. */
    List<PatientPushNotification> findByTargetDoctorUsernameAndHospitalCodeOrderByPushedAtDesc(
            String targetDoctorUsername, String hospitalCode);

    /** Count of unread notifications for a specific doctor at a specific hospital. */
    long countByTargetDoctorUsernameAndHospitalCodeAndIsReadFalse(
            String targetDoctorUsername, String hospitalCode);
}
