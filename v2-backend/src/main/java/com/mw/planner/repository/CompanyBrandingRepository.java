package com.mw.planner.repository;

import com.mw.planner.domain.CompanyBranding;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CompanyBrandingRepository extends MongoRepository<CompanyBranding, String> {
  Optional<CompanyBranding> findByCompanyId(String companyId);
}
