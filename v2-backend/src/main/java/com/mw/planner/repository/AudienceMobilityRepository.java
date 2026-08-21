package com.mw.planner.repository;

import com.mw.planner.domain.AudienceMobility;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AudienceMobilityRepository extends MongoRepository<AudienceMobility, String> {
  long countByCountryId(String countryId);
}
