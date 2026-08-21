package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Verifies the spot-duration filter ($in on the physical slot length digitalFields.spotDuration) on
 * the BROWSE path so "View All Inventories" lists only screens whose slot equals the selected
 * duration — a single value is an exact match, excluding a 10s-slot screen for a 15s creative (Zoho
 * #20805; repointed from the sellable prices.durationSeconds to the physical spotDuration so it
 * agrees with the booking constraint). Mocks MongoTemplate and inspects the captured query JSON.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - digitalFields.spotDuration spot-duration filter")
class InventoryRepositoryImplDurationsTest {

  private static final String DURATIONS_PATH = "digitalFields.spotDuration";

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private InventoryRepositoryImpl repo;

  private void stubEmpty() {
    when(mongoTemplate.count(any(Query.class), eq(Inventory.class))).thenReturn(0L);
    when(mongoTemplate.find(any(Query.class), eq(Inventory.class))).thenReturn(List.of());
  }

  private String runAndCaptureQueryJson(BrowseInventoryRequestDTO filterRequest) {
    repo.findActiveInventoriesByCountryPaginated(
        "MY", null, null, null, null, null, null, filterRequest, null, PageRequest.of(0, 20), null);
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(Inventory.class));
    return queryCaptor.getValue().getQueryObject().toJson();
  }

  private BrowseInventoryRequestDTO baseRequest() {
    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setCountry("MY");
    return request;
  }

  @Test
  @DisplayName("single duration → $in on prices.durationSeconds")
  void singleDuration_appliesInFilter() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setDurations(List.of(10));

    String json = runAndCaptureQueryJson(request);

    assertTrue(json.contains(DURATIONS_PATH), "Query must filter on prices.durationSeconds");
    assertTrue(json.contains("\"$in\": [10]"), "Must match selected 10s duration, got: " + json);
  }

  @Test
  @DisplayName("multiple durations → $in with all values")
  void multipleDurations_appliesInFilter() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setDurations(List.of(10, 15, 30));

    String json = runAndCaptureQueryJson(request);

    assertTrue(json.contains(DURATIONS_PATH));
    assertTrue(json.contains("10"));
    assertTrue(json.contains("15"));
    assertTrue(json.contains("30"));
  }

  @Test
  @DisplayName("null durations → no duration criteria")
  void nullDurations_noCriteria() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();

    String json = runAndCaptureQueryJson(request);

    assertFalse(json.contains(DURATIONS_PATH), "No duration filter when durations is null");
  }

  @Test
  @DisplayName("empty durations list → no duration criteria")
  void emptyDurations_noCriteria() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setDurations(List.of());

    String json = runAndCaptureQueryJson(request);

    assertFalse(json.contains(DURATIONS_PATH), "No duration filter when durations is empty");
  }

  @Test
  @DisplayName("durations coexist with other filters; base country/archived criteria preserved")
  void coexistsWithOtherFilters() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setDurations(List.of(10));
    request.setTypes(List.of("OOH"));

    String json = runAndCaptureQueryJson(request);

    assertTrue(json.contains(DURATIONS_PATH), "duration filter present");
    assertTrue(json.contains("\"type\""), "types filter ($in on type) must coexist");
    assertTrue(json.contains("locationHierarchy.countryName"), "country criteria preserved");
    assertTrue(json.contains("archived"), "archived criteria preserved");
  }
}
