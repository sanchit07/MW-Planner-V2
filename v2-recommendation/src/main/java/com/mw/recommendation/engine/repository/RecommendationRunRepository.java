package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.RecommendationRun;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Repository for RecommendationRun entities */
@Repository
public interface RecommendationRunRepository extends MongoRepository<RecommendationRun, String> {

  /** Find recommendation run by runId */
  Optional<RecommendationRun> findByRunId(String runId);

  /** Find recommendation run by campaignId and requestHash */
  Optional<RecommendationRun> findByCampaignIdAndRequestHash(String campaignId, String requestHash);
}
