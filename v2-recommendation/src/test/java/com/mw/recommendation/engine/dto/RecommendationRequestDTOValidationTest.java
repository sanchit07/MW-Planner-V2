package com.mw.recommendation.engine.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecommendationRequestDTO - AD_PLAYS goalValue validation")
class RecommendationRequestDTOValidationTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private RecommendationRequestDTO buildRequest(Long goalValue) {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("US");
    req.setStartDate(LocalDate.of(2025, 1, 1));
    req.setEndDate(LocalDate.of(2025, 1, 31));
    req.setGoal(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);
    req.setGoalValue(goalValue);
    return req;
  }

  @Test
  @DisplayName("AD_PLAYS goalValue=100 (min) is valid")
  void adPlays_goalValueAtMin_valid() {
    Set<ConstraintViolation<RecommendationRequestDTO>> violations =
        validator.validate(buildRequest(100L));
    assertTrue(
        violations.stream().noneMatch(v -> v.getPropertyPath().toString().contains("goalValue")),
        "goalValue=100 must be valid");
  }

  @Test
  @DisplayName("AD_PLAYS goalValue=1000000 (max) is valid")
  void adPlays_goalValueAtMax_valid() {
    Set<ConstraintViolation<RecommendationRequestDTO>> violations =
        validator.validate(buildRequest(1_000_000L));
    assertTrue(
        violations.stream().noneMatch(v -> v.getPropertyPath().toString().contains("goalValue")),
        "goalValue=1000000 must be valid");
  }

  @Test
  @DisplayName("AD_PLAYS goalValue=500000 (mid range) is valid")
  void adPlays_goalValueMidRange_valid() {
    Set<ConstraintViolation<RecommendationRequestDTO>> violations =
        validator.validate(buildRequest(500_000L));
    assertTrue(
        violations.stream().noneMatch(v -> v.getPropertyPath().toString().contains("goalValue")),
        "goalValue=500000 must be valid");
  }

  @Test
  @DisplayName("AD_PLAYS goalValue=99 (below min) is invalid")
  void adPlays_goalValueBelowMin_invalid() {
    Set<ConstraintViolation<RecommendationRequestDTO>> violations =
        validator.validate(buildRequest(99L));
    assertTrue(
        violations.stream()
            .anyMatch(
                v ->
                    v.getMessage()
                        .contains("goalValue for AD_PLAYS must be between 100 and 1000000")),
        "goalValue=99 must fail validation");
  }

  @Test
  @DisplayName("AD_PLAYS goalValue=1000001 (above max) is invalid")
  void adPlays_goalValueAboveMax_invalid() {
    Set<ConstraintViolation<RecommendationRequestDTO>> violations =
        validator.validate(buildRequest(1_000_001L));
    assertTrue(
        violations.stream()
            .anyMatch(
                v ->
                    v.getMessage()
                        .contains("goalValue for AD_PLAYS must be between 100 and 1000000")),
        "goalValue=1000001 must fail validation");
  }

  @Test
  @DisplayName("AD_PLAYS goalValue=null is valid (goalValue is optional)")
  void adPlays_goalValueNull_valid() {
    Set<ConstraintViolation<RecommendationRequestDTO>> violations =
        validator.validate(buildRequest(null));
    assertTrue(
        violations.stream().noneMatch(v -> v.getPropertyPath().toString().contains("goalValue")),
        "null goalValue must be valid — it is optional");
  }

  @Test
  @DisplayName("IMPRESSIONS goalValue=50 is valid (validation only applies to AD_PLAYS)")
  void impressions_anyGoalValue_valid() {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("US");
    req.setStartDate(LocalDate.of(2025, 1, 1));
    req.setEndDate(LocalDate.of(2025, 1, 31));
    req.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    req.setGoalValue(50L);

    Set<ConstraintViolation<RecommendationRequestDTO>> violations = validator.validate(req);
    assertTrue(
        violations.stream().noneMatch(v -> v.getPropertyPath().toString().contains("goalValue")),
        "IMPRESSIONS goalValue=50 must be valid — AD_PLAYS constraint does not apply");
  }
}
