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
 * Verifies the sellingTerm.minDays availability criteria added to the recommendation submission
 * fetch path (findActiveInventoriesByCountryWithGeographyTargeting, aggregation-based). Mocks
 * MongoTemplate and inspects the captured aggregation pipeline, mirroring the browse-path test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - geo-targeting fetch sellingTerm.minDays filter")
class InventoryRepositoryImplGeoTargetingMinDaysTest {

  private static final String MIN_DAYS_PATH = "sellingTerm.minDays";

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private InventoryRepositoryImpl repo;

  @SuppressWarnings("unchecked")
  private String runAndCaptureAggregationJson(Long durationDays) {
    AggregationResults<Inventory> emptyResults = mock(AggregationResults.class);
    when(emptyResults.getMappedResults()).thenReturn(Collections.emptyList());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("inventories"), eq(Inventory.class)))
        .thenReturn(emptyResults);

    repo.findActiveInventoriesByCountryWithGeographyTargeting(
        "MY",
        null,
        null,
        null,
        null,
        null,
        null,
        durationDays,
        null,
        null,
        null,
        null,
        null,
        false);

    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    // Normalise whitespace so the assertion is agnostic to the renderer's spacing around ':'.
    return aggCaptor.getValue().toString().replace(" ", "");
  }

  @Test
  @DisplayName("durationDays = 3 → $lte equals inclusive duration")
  void durationPresent_appliesLteDuration() {
    String json = runAndCaptureAggregationJson(3L);

    assertTrue(json.contains(MIN_DAYS_PATH), "Pipeline must filter on sellingTerm.minDays");
    assertTrue(json.contains("\"$lte\":3"), "Duration must be 3, got: " + json);
    assertTrue(
        json.contains("$exists"), "Lenient $or must include an $exists branch, got: " + json);
    assertTrue(json.contains("$or"), "minDays filter must be a lenient $or, got: " + json);
  }

  @Test
  @DisplayName("durationDays = 1 (e.g. clamped inverted range) → $lte: 1")
  void durationOne_appliesLteOne() {
    String json = runAndCaptureAggregationJson(1L);

    assertTrue(json.contains(MIN_DAYS_PATH));
    assertTrue(json.contains("\"$lte\":1"), "Duration must be 1, got: " + json);
  }

  @Test
  @DisplayName("durationDays null → no minDays stage (backward compatible)")
  void durationNull_noCriteria() {
    String json = runAndCaptureAggregationJson(null);

    assertFalse(json.contains(MIN_DAYS_PATH), "No minDays filter when durationDays is null");
  }

  @Test
  @DisplayName("minDays stage coexists with classification/country/pricing stages")
  void coexistsWithOtherFilters() {
    AggregationResults<Inventory> emptyResults = mock(AggregationResults.class);
    when(emptyResults.getMappedResults()).thenReturn(Collections.emptyList());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("inventories"), eq(Inventory.class)))
        .thenReturn(emptyResults);

    repo.findActiveInventoriesByCountryWithGeographyTargeting(
        "MY",
        null,
        null,
        null,
        null,
        List.of("Digital"),
        null,
        3L,
        null,
        null,
        null,
        null,
        null,
        false);

    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    String json = aggCaptor.getValue().toString().replace(" ", "");

    assertTrue(json.contains(MIN_DAYS_PATH), "minDays filter present");
    assertTrue(json.contains("\"$lte\":3"));
    assertTrue(json.contains("locationHierarchy.countryName"), "country criteria preserved");
    assertTrue(json.contains("Digital"), "classification filter preserved");
  }
}
