package com.fhir.hospitalB.repository;

import com.fhir.hospitalB.model.HospitalBOPConsultEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HospitalBOPConsultRepository
        extends MongoRepository<HospitalBOPConsultEntity, String> {
    List<HospitalBOPConsultEntity> findByAbhaIdOrderByReceivedAtDesc(String abhaId);
    java.util.Optional<HospitalBOPConsultEntity> findFirstByAbhaIdOrderByReceivedAtDesc(String abhaId);
}
