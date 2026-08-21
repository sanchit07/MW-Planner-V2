package com.mw.planner.repository;

import com.mw.planner.domain.Demographics;
import com.mw.planner.enums.DemographicsType;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemographicsRepository extends MongoRepository<Demographics, String> {
  Optional<Demographics> findByDemoTypeAndDemoKey(DemographicsType demoType, String demoKey);
}
