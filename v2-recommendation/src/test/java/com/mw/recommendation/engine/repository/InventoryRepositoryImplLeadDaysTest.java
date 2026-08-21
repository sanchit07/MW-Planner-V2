package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Verifies the sellingTerm.leadDays lead-time eligibility criteria added to BOTH the browse path
 * (findActiveInventoriesByCountryPaginated, Query-based) and the recommendation submission fetch
 * path (findActiveInventoriesByCountryWithGeographyTargeting, aggregation-based). Mocks
 * MongoTemplate and inspects the captured query / pipeline JSON, mirroring the minDays tests.
 *
 * <p>The leadDays criteria is LENIENT (opposite of minDays): an inventory is eligible when leadDays
 * is missing / null / {@code <= 0}, OR when leadDays {@code <= availableLeadDays}. So the rendered
 * filter is a 4-branch {@code $or} over sellingTerm.leadDays.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - sellingTerm.leadDays eligibility filter")
class InventoryRepositoryImplLeadDaysTest {

  private static final String LEAD_DAYS_PATH = "sellingTerm.leadDays";

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private InventoryRepositoryImpl repo;

  @Nested
  @DisplayName("BROWSE path (findActiveInventoriesByCountryPaginated)")
  class BrowsePath {

    private void stubEmpty() {
      when(mongoTemplate.count(any(Query.class), eq(Inventory.class))).thenReturn(0L);
      when(mongoTemplate.find(any(Query.class), eq(Inventory.class))).thenReturn(List.of());
    }

    private String runAndCaptureQueryJson(Long availableLeadDays) {
      stubEmpty();
      BrowseInventoryRequestDTO filterRequest = new BrowseInventoryRequestDTO();
      filterRequest.setCountry("MY");
      repo.findActiveInventoriesByCountryPaginated(
          "MY",
          null,
          null,
          null,
          null,
          null,
          null,
          filterRequest,
          availableLeadDays,
          PageRequest.of(0, 20),
          null);
      ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
      verify(mongoTemplate).count(queryCaptor.capture(), eq(Inventory.class));
      return queryCaptor.getValue().getQueryObject().toJson();
    }

    @Test
    @DisplayName(
        "availableLeadDays = 1 → 4-branch $or on sellingTerm.leadDays (exists/null/<=0/<=1)")
    void leadDaysPresent_appliesOrCriteria() {
      String json = runAndCaptureQueryJson(1L);

      assertTrue(json.contains(LEAD_DAYS_PATH), "Query must filter on sellingTerm.leadDays");
      assertTrue(json.contains("$exists"), "Must include the exists:false branch");
      assertTrue(json.contains("$lte"), "Must include the <= branches");
      // Boundary inventory leadDays=1 is admitted by the $lte:1 branch; leadDays=3 is excluded.
      assertTrue(json.contains("\"$lte\": 1"), "Must allow leadDays <= availableLeadDays(=1)");
      assertTrue(json.contains("\"$lte\": 0"), "Must keep the explicit <=0 (null/zero) branch");
    }

    @Test
    @DisplayName("availableLeadDays = 0 (start today) → still lenient, <=0 branch keeps null/zero")
    void leadDaysZero_keepsLenientBranch() {
      String json = runAndCaptureQueryJson(0L);

      assertTrue(json.contains(LEAD_DAYS_PATH));
      assertTrue(json.contains("\"$lte\": 0"), "Zero/negative gap still admits null/0 leadDays");
    }

    @Test
    @DisplayName("availableLeadDays = -5 (start in the past) → <=0 branch still admits null/0")
    void leadDaysNegative_keepsLenientBranch() {
      String json = runAndCaptureQueryJson(-5L);

      assertTrue(json.contains(LEAD_DAYS_PATH));
      assertTrue(
          json.contains("\"$lte\": 0"), "Past start still admits null/0 leadDays inventories");
    }

    @Test
    @DisplayName("availableLeadDays null (no startDate) → no leadDays criteria")
    void leadDaysNull_noCriteria() {
      String json = runAndCaptureQueryJson(null);

      assertFalse(
          json.contains(LEAD_DAYS_PATH), "No leadDays filter when availableLeadDays is null");
    }

    @Test
    @DisplayName("base country/archived criteria preserved alongside leadDays")
    void coexistsWithBaseCriteria() {
      String json = runAndCaptureQueryJson(1L);

      assertTrue(json.contains(LEAD_DAYS_PATH), "leadDays filter present");
      assertTrue(json.contains("locationHierarchy.countryName"), "country criteria preserved");
      assertTrue(json.contains("archived"), "archived criteria preserved");
    }
  }

  @Nested
  @DisplayName("RECOMMENDATIONS path (findActiveInventoriesByCountryWithGeographyTargeting)")
  class RecommendationsPath {

    @SuppressWarnings("unchecked")
    private String runAndCaptureAggregationJson(Long availableLeadDays) {
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
          null,
          availableLeadDays,
          null,
          null,
          null,
          null,
          false);

      ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
      verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
      // Normalise whitespace so assertions are agnostic to renderer spacing around ':'.
      return aggCaptor.getValue().toString().replace(" ", "");
    }

    @Test
    @DisplayName("availableLeadDays = 1 → 4-branch $or on sellingTerm.leadDays in pipeline")
    void leadDaysPresent_appliesOrCriteria() {
      String json = runAndCaptureAggregationJson(1L);

      assertTrue(json.contains(LEAD_DAYS_PATH), "Pipeline must filter on sellingTerm.leadDays");
      assertTrue(json.contains("$exists"), "Must include the exists:false branch");
      assertTrue(json.contains("\"$lte\":1"), "Must allow leadDays <= availableLeadDays(=1)");
      assertTrue(json.contains("\"$lte\":0"), "Must keep the explicit <=0 (null/zero) branch");
    }

    @Test
    @DisplayName("availableLeadDays null → no leadDays stage in pipeline")
    void leadDaysNull_noCriteria() {
      String json = runAndCaptureAggregationJson(null);

      assertFalse(
          json.contains(LEAD_DAYS_PATH), "No leadDays stage when availableLeadDays is null");
    }
  }
}
