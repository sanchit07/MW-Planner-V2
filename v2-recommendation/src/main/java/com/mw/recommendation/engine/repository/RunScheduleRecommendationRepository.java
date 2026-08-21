package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.RunScheduleRecommendation;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RunScheduleRecommendationRepository
    extends MongoRepository<RunScheduleRecommendation, String> {

  List<RunScheduleRecommendation> findByRunId(String runId);

  List<RunScheduleRecommendation> findByRunIdAndInventoryIdIn(
      String runId, List<String> inventoryIds);

  void deleteByRunId(String runId);

  void deleteByRunIdAndInventoryIdIn(String runId, List<String> inventoryIds);
}
