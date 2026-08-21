package com.mw.recommendation.engine.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NormalizationUtilsTest {

  @Test
  void testNormalize_WithValidValues_ReturnsNormalizedValue() {
    // Test normal case
    Double result = NormalizationUtils.normalize(50.0, 0.0, 100.0);
    assertEquals(50.0, result, 0.01);

    // Test minimum value
    result = NormalizationUtils.normalize(0.0, 0.0, 100.0);
    assertEquals(0.0, result, 0.01);

    // Test maximum value
    result = NormalizationUtils.normalize(100.0, 0.0, 100.0);
    assertEquals(100.0, result, 0.01);

    // Test middle value
    result = NormalizationUtils.normalize(25.0, 0.0, 100.0);
    assertEquals(25.0, result, 0.01);
  }

  @Test
  void testNormalize_WithDifferentRange_ReturnsCorrectNormalizedValue() {
    // Test with different min/max range
    Double result = NormalizationUtils.normalize(5.0, 1.0, 5.0);
    assertEquals(100.0, result, 0.01);

    result = NormalizationUtils.normalize(3.0, 1.0, 5.0);
    assertEquals(50.0, result, 0.01);

    result = NormalizationUtils.normalize(1.0, 1.0, 5.0);
    assertEquals(0.0, result, 0.01);
  }

  @Test
  void testNormalize_WithNullValues_ReturnsNull() {
    assertNull(NormalizationUtils.normalize(null, 0.0, 100.0));
    assertNull(NormalizationUtils.normalize(50.0, null, 100.0));
    assertNull(NormalizationUtils.normalize(50.0, 0.0, null));
    assertNull(NormalizationUtils.normalize(null, null, null));
  }

  @Test
  void testNormalize_WithEqualMinMax_ReturnsNeutralScore() {
    Double result = NormalizationUtils.normalize(50.0, 10.0, 10.0);
    assertEquals(50.0, result, 0.01);
  }

  @Test
  void testNormalize_WithValueBelowMin_ClampsToZero() {
    Double result = NormalizationUtils.normalize(-10.0, 0.0, 100.0);
    assertEquals(0.0, result, 0.01);
  }

  @Test
  void testNormalize_WithValueAboveMax_ClampsToHundred() {
    Double result = NormalizationUtils.normalize(150.0, 0.0, 100.0);
    assertEquals(100.0, result, 0.01);
  }
}
