package com.mw.planner.repository;

import com.mw.planner.domain.PlannerConfiguration;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlannerConfigurationRepository
    extends MongoRepository<PlannerConfiguration, String> {
  Optional<PlannerConfiguration> findByCompanyId(String companyId);
}
