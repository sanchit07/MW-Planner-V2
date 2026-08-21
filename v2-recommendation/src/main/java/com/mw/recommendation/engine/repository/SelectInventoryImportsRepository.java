package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.SelectInventoryImports;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Repository for retained CSV inventory imports. All lookups are tenant-scoped by companyId. */
@Repository
public interface SelectInventoryImportsRepository
    extends MongoRepository<SelectInventoryImports, String> {

  Page<SelectInventoryImports> findByCompanyIdAndCampaignId(
      String companyId, String campaignId, Pageable pageable);

  Optional<SelectInventoryImports> findByIdAndCompanyId(String id, String companyId);

  void deleteByIdAndCompanyId(String id, String companyId);
}
