package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import java.util.Collections;
import java.util.List;
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
 * Selected spot-duration filter on the RECOMMENDATIONS (scored) fetch. When the run carries
 * durations, only screens whose physical slot length (digitalFields.spotDuration) is in that list
 * are scored — so a 10s-slot screen is never recommended for a 15s creative and the booking
 * "creativeDuration exceeds spotDuration" reject can't happen. Mirrors {@link
 * InventoryRepositoryImplGoalPricingTest}: mocks MongoTemplate and inspects the captured
 * aggregation pipeline.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - scored fetch digitalFields.spotDuration filter")
class InventoryRepositoryImplScoredDurationsTest {

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private InventoryRepositoryImpl repo;

  @SuppressWarnings("unchecked")
  private String runAndCaptureAggregationJson(List<Integer> durations) {
    AggregationResults<Inventory> empty = mock(AggregationResults.class);
    when(empty.getMappedResults()).thenReturn(Collections.emptyList());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("inventories"), eq(Inventory.class)))
        .thenReturn(empty);

    repo.findActiveInventoriesByCountryWithGeographyTargeting(
        "IN",
        null,
        null,
        null,
        null,
        List.of("Digital"),
        null,
        null,
        null,
        null,
        null,
        null,
        durations);

    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    return aggCaptor.getValue().toString().replace(" ", "");
  }

  @Test
  @DisplayName("single duration → $in on digitalFields.spotDuration (exact for one value)")
  void singleDuration_appliesInFilter() {
    String json = runAndCaptureAggregationJson(List.of(15));
    assertTrue(
        json.contains("\"digitalFields.spotDuration\":{\"$in\":[15]}"),
        "Scored fetch must exact-match spotDuration 15. Pipeline: " + json);
  }

  @Test
  @DisplayName("multiple durations → $in with all values")
  void multipleDurations_appliesInFilter() {
    String json = runAndCaptureAggregationJson(List.of(10, 15, 30));
    assertTrue(
        json.contains("\"digitalFields.spotDuration\":{\"$in\":[10,15,30]}"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("null durations → no spotDuration stage (via the back-compat overload)")
  void nullDurations_noStage() {
    // The 12-arg overload delegates with durations=null → the pipeline has no spotDuration stage.
    AggregationResults<Inventory> empty = mock(AggregationResults.class);
    when(empty.getMappedResults()).thenReturn(Collections.emptyList());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("inventories"), eq(Inventory.class)))
        .thenReturn(empty);

    repo.findActiveInventoriesByCountryWithGeographyTargeting(
        "IN", null, null, null, null, List.of("Digital"), null, null, null, null, null, null);

    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    assertFalse(
        aggCaptor.getValue().toString().contains("digitalFields.spotDuration"),
        "No spotDuration stage when durations is null");
  }

  @Test
  @DisplayName("empty durations → no spotDuration stage")
  void emptyDurations_noStage() {
    String json = runAndCaptureAggregationJson(List.of());
    assertFalse(json.contains("digitalFields.spotDuration"), "No stage for an empty list");
  }
}
