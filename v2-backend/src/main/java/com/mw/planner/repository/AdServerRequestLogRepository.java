package com.mw.planner.repository;

import com.mw.planner.domain.AdServerRequestLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB repository for AdServerRequestLog documents. Provides basic CRUD operations for ad server
 * API call logging.
 */
@Repository
public interface AdServerRequestLogRepository extends MongoRepository<AdServerRequestLog, String> {

  // No custom query methods needed initially
  // Future enhancements could add:
  // - List<AdServerRequestLog> findByCampaignIdOrderByCreatedAtDesc(String campaignId);
  // - List<AdServerRequestLog> findByResponseCodeGreaterThanEqual(Integer responseCode);
  // - Page<AdServerRequestLog> findByEndpointContaining(String endpoint, Pageable pageable);
}
