package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.SelectionMode;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Repository for RecommendationResult entities */
@Repository
public interface RecommendationResultRepository
    extends MongoRepository<RecommendationResult, String>, RecommendationResultRepositoryCustom {

  /** Find all results for a runId with custom sorting */
  Page<RecommendationResult> findByRunId(String runId, Pageable pageable);

  /** Count results for a runId */
  long countByRunId(String runId);

  /** Find results by runId and inventoryIds */
  List<RecommendationResult> findByRunIdAndInventoryIdIn(String runId, List<String> inventoryIds);

  /** Find results for a runId by selection mode */
  List<RecommendationResult> findByRunIdAndSelectionMode(String runId, SelectionMode selectionMode);

  /** Find all results for a runId */
  List<RecommendationResult> findByRunId(String runId);

  /** Delete all results for a runId */
  void deleteByRunId(String runId);
}
