package com.mw.planner.repository;

import com.mw.planner.domain.UserDashboardConfig;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDashboardConfigRepository
    extends MongoRepository<UserDashboardConfig, String> {
  Optional<UserDashboardConfig> findByUserIdAndCompanyId(String userId, String companyId);
}
