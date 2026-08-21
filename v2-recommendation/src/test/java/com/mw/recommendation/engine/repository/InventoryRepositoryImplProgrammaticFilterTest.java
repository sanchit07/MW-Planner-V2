package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

/**
 * Verifies the recommendation fetch path ({@code
 * findActiveInventoriesByCountryWithGeographyTargeting}, aggregation-based) for the {@code dsps}
 * and {@code programmaticEnabled} filters. Mocks MongoTemplate and inspects the built aggregation
 * pipeline rather than hitting a database.
 */
@ExtendWith(MockitoExtension.class)
class InventoryRepositoryImplProgrammaticFilterTest {

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private InventoryRepositoryImpl repository;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    AggregationResults<Inventory> emptyResults = mock(AggregationResults.class);
    lenient().when(emptyResults.getMappedResults()).thenReturn(Collections.emptyList());
    lenient()
        .when(
            mongoTemplate.aggregate(any(Aggregation.class), eq("inventories"), eq(Inventory.class)))
        .thenReturn(emptyResults);
  }

  private String captureAggregation(
      List<String> classifications, List<String> dsps, boolean programmaticEnabled) {
    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    repository.findActiveInventoriesByCountryWithGeographyTargeting(
        "Japan",
        null,
        null,
        null,
        null,
        classifications,
        null,
        null,
        null,
        null,
        null,
        null,
        dsps,
        programmaticEnabled);
    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    return aggCaptor.getValue().toString();
  }

  @Test
  @DisplayName(
      "programmatic + [Digital, Classic]: Digital slice needs programmaticDealTypes, Classic passes")
  void programmatic_digitalAndClassic_refinesOnlyDigital() {
    String pipeline = captureAggregation(List.of("Digital", "Classic"), null, true);

    assertTrue(
        pipeline.contains("programmaticDealTypes.0"),
        "Digital arm must require non-empty programmaticDealTypes");
    assertTrue(pipeline.contains("Classic"), "Classic arm must be present and unrestricted");
    assertTrue(pipeline.contains("$or"), "Digital-programmatic OR other-classifications structure");
  }

  @Test
  @DisplayName("programmatic + [Digital] only: only programmatic Digital")
  void programmatic_digitalOnly_requiresProgrammatic() {
    String pipeline = captureAggregation(List.of("Digital"), null, true);

    assertTrue(
        pipeline.contains("programmaticDealTypes.0"), "Digital must require programmaticDealTypes");
    assertTrue(pipeline.contains("Digital"), "Digital classification constraint present");
    assertFalse(pipeline.contains("Classic"), "No Classic arm when only Digital requested");
  }

  @Test
  @DisplayName("programmatic + [Classic] only: no-op (no programmatic constraint)")
  void programmatic_classicOnly_isNoOp() {
    String pipeline = captureAggregation(List.of("Classic"), null, true);

    assertFalse(
        pipeline.contains("programmaticDealTypes"),
        "programmatic flag is a no-op when Digital is not requested");
    assertTrue(pipeline.contains("Classic"), "Classic classification still filtered");
  }

  @Test
  @DisplayName(
      "programmatic + no classifications: non-Digital passes, any Digital must be programmatic")
  void programmatic_noClassifications_refinesDigitalGlobally() {
    String pipeline = captureAggregation(null, null, true);

    assertTrue(
        pipeline.contains("programmaticDealTypes.0"), "Digital slice must require programmatic");
    assertTrue(pipeline.contains("$ne"), "Non-Digital arm expressed as classification $ne Digital");
    assertTrue(pipeline.contains("Digital"), "Digital referenced in both arms");
  }

  @Test
  @DisplayName("dsps filter adds a classification-agnostic $in match")
  void dsps_filter_addsInMatch() {
    String pipeline = captureAggregation(List.of("Digital"), List.of("MAX", "LMX"), false);

    assertTrue(pipeline.contains("dsps"), "dsps $match must be present");
    assertTrue(pipeline.contains("MAX") && pipeline.contains("LMX"), "requested DSPs present");
    assertFalse(
        pipeline.contains("programmaticDealTypes"),
        "programmatic constraint absent when programmaticEnabled is false");
  }

  @Test
  @DisplayName("programmaticEnabled=false preserves plain classification $in behavior")
  void noProgrammatic_classificationsUnchanged() {
    String pipeline = captureAggregation(List.of("Digital", "Classic"), null, false);

    assertFalse(
        pipeline.contains("programmaticDealTypes"),
        "no programmatic constraint when flag is false");
    assertTrue(
        pipeline.contains("Digital") && pipeline.contains("Classic"), "both classifications");
    assertTrue(pipeline.contains("$in"), "classification IN list");
  }
}
