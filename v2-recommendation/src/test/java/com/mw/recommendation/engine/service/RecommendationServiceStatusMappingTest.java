package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import org.junit.jupiter.api.Test;

/**
 * v1 regression guard for finding #1's shared plumbing. Adding the FAILED status must not change
 * how v1 statuses are mapped: v1 runs are only ever IN_PROGRESS or COMPLETED (and legacy/null), and
 * those must map exactly as before. FAILED (only produced by v2) maps to FAILED.
 */
class RecommendationServiceStatusMappingTest {

  @Test
  void v1StatusesMapUnchanged() {
    assertEquals(
        RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS,
        RecommendationService.mapRunStatus(RecommendationRun.RunStatus.IN_PROGRESS));
    assertEquals(
        RecommendationStatusResponseDTO.RunStatus.COMPLETED,
        RecommendationService.mapRunStatus(RecommendationRun.RunStatus.COMPLETED));
  }

  @Test
  void legacyNullStatusStillMapsToCompleted() {
    // Preserves the pre-change behavior of the old ternary (anything not IN_PROGRESS -> COMPLETED).
    assertEquals(
        RecommendationStatusResponseDTO.RunStatus.COMPLETED,
        RecommendationService.mapRunStatus(null));
  }

  @Test
  void failedStatusIsSurfaced() {
    assertEquals(
        RecommendationStatusResponseDTO.RunStatus.FAILED,
        RecommendationService.mapRunStatus(RecommendationRun.RunStatus.FAILED));
  }
}
