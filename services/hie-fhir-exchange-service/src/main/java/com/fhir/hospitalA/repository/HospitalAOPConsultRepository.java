package com.fhir.hospitalA.repository;

import com.fhir.hospitalA.model.HospitalAOPConsultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalAOPConsultRepository extends JpaRepository<HospitalAOPConsultEntity, Long> {
    Optional<HospitalAOPConsultEntity> findFirstByAbhaIdOrderByIdDesc(String abhaId);
    List<HospitalAOPConsultEntity> findByAbhaIdOrderByCreatedAtDesc(String abhaId);
    List<HospitalAOPConsultEntity> findAllByOrderByCreatedAtDesc();
}
