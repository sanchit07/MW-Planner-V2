package com.mw.recommendation.engine.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecommendationRequestDTOSearchKeywordsTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private RecommendationRequestDTO validBase() {
    RecommendationRequestDTO r = new RecommendationRequestDTO();
    r.setCountry("MY");
    r.setStartDate(LocalDate.of(2026, 7, 1));
    r.setEndDate(LocalDate.of(2026, 7, 31));
    return r;
  }

  private Set<ConstraintViolation<RecommendationRequestDTO>> violations(
      RecommendationRequestDTO r) {
    return validator.validate(r);
  }

  @Test
  void nullSearchKeywordsIsValid() {
    assertTrue(violations(validBase()).isEmpty());
  }

  @Test
  void emptySearchKeywordsIsValid() {
    RecommendationRequestDTO r = validBase();
    r.setSearchKeywords(List.of());
    assertTrue(violations(r).isEmpty());
  }

  @Test
  void normalKeywordsAreValid() {
    RecommendationRequestDTO r = validBase();
    r.setSearchKeywords(List.of("Kuala Lumpur", "Cyberjaya"));
    assertTrue(violations(r).isEmpty());
  }

  @Test
  void blankKeywordIsRejected() {
    RecommendationRequestDTO r = validBase();
    r.setSearchKeywords(List.of("Kuala Lumpur", "   "));
    assertFalse(violations(r).isEmpty());
  }

  @Test
  void nullElementIsRejected() {
    RecommendationRequestDTO r = validBase();
    r.setSearchKeywords(java.util.Arrays.asList("KL", null));
    assertFalse(violations(r).isEmpty());
  }

  @Test
  void overlongKeywordIsRejected() {
    RecommendationRequestDTO r = validBase();
    r.setSearchKeywords(List.of("x".repeat(101)));
    assertFalse(violations(r).isEmpty());
  }

  @Test
  void tooManyKeywordsRejected() {
    RecommendationRequestDTO r = validBase();
    r.setSearchKeywords(Collections.nCopies(21, "kl"));
    assertFalse(violations(r).isEmpty());
  }
}
