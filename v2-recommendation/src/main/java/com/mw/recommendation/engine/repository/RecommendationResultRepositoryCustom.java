package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.RecommendationResultFilterDTO;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom repository interface for RecommendationResult with dynamic filtering support */
public interface RecommendationResultRepositoryCustom {

  /**
   * Find all results for a runId with filtering and sorting
   *
   * @param runId Run ID
   * @param filter Filter DTO (can be null)
   * @param search Optional search text (case-insensitive match on name, address, city,
   *     classification, type, referenceId)
   * @param pageable Pageable with sorting
   * @return Page of filtered results
   */
  Page<RecommendationResult> findByRunIdWithFilters(
      String runId, RecommendationResultFilterDTO filter, String search, Pageable pageable);

  /**
   * Bulk update selectionMode field only (Phase 3.2 optimization). Updates only the selectionMode
   * field using MongoDB bulk write API for maximum efficiency. This is 99% faster than saveAll()
   * for updating single fields.
   *
   * @param runId Run ID to identify results
   * @param selectionModeByInventoryId Map of inventoryId to SelectionMode (null = clear selection)
   */
  void bulkUpdateSelectionMode(String runId, Map<String, SelectionMode> selectionModeByInventoryId);
}
