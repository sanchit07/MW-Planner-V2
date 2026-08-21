package com.mw.recommendation.engine.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VariationUtilsTest {

  @Test
  void testApplyJitter_WithValidInput_ReturnsJitteredScore() {
    Double score = 50.0;
    String runId = "test-run-id";

    Double result = VariationUtils.applyJitter(score, runId);

    assertNotNull(result);
    // Jitter is ±7.5%, so result should be between 46.25 and 53.75
    assertTrue(result >= 46.25 && result <= 53.75);
  }

  @Test
  void testApplyJitter_WithSameRunId_ReturnsDeterministicResult() {
    Double score = 50.0;
    String runId = "test-run-id";

    Double result1 = VariationUtils.applyJitter(score, runId);
    Double result2 = VariationUtils.applyJitter(score, runId);

    // Should be deterministic (same seed = same jitter)
    assertEquals(result1, result2, 0.001);
  }

  @Test
  void testApplyJitter_WithDifferentRunId_ReturnsDifferentResult() {
    Double score = 50.0;

    Double result1 = VariationUtils.applyJitter(score, "run-id-1");
    Double result2 = VariationUtils.applyJitter(score, "run-id-2");

    // Different runIds should produce different jitter
    assertNotEquals(result1, result2);
  }

  @Test
  void testApplyJitter_WithNullScore_ReturnsNull() {
    assertNull(VariationUtils.applyJitter(null, "test-run-id"));
  }

  @Test
  void testApplyJitter_WithNullRunId_ReturnsOriginalScore() {
    Double score = 50.0;
    Double result = VariationUtils.applyJitter(score, null);
    assertEquals(score, result);
  }

  @Test
  void testApplyJitter_WithZeroScore_StaysAtZero() {
    Double result = VariationUtils.applyJitter(0.0, "test-run-id");
    assertEquals(0.0, result, 0.01);
  }

  @Test
  void testApplyJitter_WithMaxScore_ClampsToHundred() {
    Double result = VariationUtils.applyJitter(100.0, "test-run-id");
    assertTrue(result <= 100.0);
    assertTrue(result >= 0.0);
  }

  @Test
  void testApplyJitter_WithHighScore_ClampsToHundred() {
    // Even if jitter pushes it above 100, should clamp
    Double score = 95.0;
    Double result = VariationUtils.applyJitter(score, "test-run-id");
    assertTrue(result <= 100.0);
    assertTrue(result >= 0.0);
  }
}
