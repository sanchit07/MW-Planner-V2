package com.mw.recommendation.engine.v3.repository;

import com.mw.recommendation.engine.v3.domain.RecommendationRunV3;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RunV3Repository extends MongoRepository<RecommendationRunV3, String> {

  Optional<RecommendationRunV3> findByRunId(String runId);

  Optional<RecommendationRunV3> findByCampaignIdAndRequestHash(
      String campaignId, String requestHash);
}
