package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import java.time.LocalDate;
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
import org.springframework.data.mongodb.core.query.Query;

/**
 * Verifies the sellingTerm.minDays availability criteria added to the BROWSE path. Mocks
 * MongoTemplate and inspects the captured query JSON, matching the existing repository test style.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - sellingTerm.minDays availability filter")
class InventoryRepositoryImplMinDaysTest {

  private static final String MIN_DAYS_PATH = "sellingTerm.minDays";

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
  @DisplayName("both dates set → $lte equals inclusive duration (Jan 1 -> Jan 3 = 3)")
  void bothDates_appliesLteDuration() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setStartDate(LocalDate.of(2025, 1, 1));
    request.setEndDate(LocalDate.of(2025, 1, 3));

    String json = runAndCaptureQueryJson(request);

    assertTrue(json.contains(MIN_DAYS_PATH), "Query must filter on sellingTerm.minDays");
    assertTrue(json.contains("\"$lte\": 3"), "Inclusive duration must be 3, got: " + json);
    assertTrue(
        json.contains("$exists"), "Lenient $or must include an $exists branch, got: " + json);
    assertTrue(json.contains("$or"), "minDays filter must be a lenient $or, got: " + json);
  }

  @Test
  @DisplayName("same-day campaign → $lte: 1")
  void sameDay_appliesLteOne() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setStartDate(LocalDate.of(2025, 1, 1));
    request.setEndDate(LocalDate.of(2025, 1, 1));

    String json = runAndCaptureQueryJson(request);

    assertTrue(json.contains(MIN_DAYS_PATH));
    assertTrue(json.contains("\"$lte\": 1"), "Same-day duration must be 1, got: " + json);
  }

  @Test
  @DisplayName("startDate null → no minDays criteria")
  void startDateNull_noCriteria() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setEndDate(LocalDate.of(2025, 1, 3));

    String json = runAndCaptureQueryJson(request);

    assertFalse(json.contains(MIN_DAYS_PATH), "No minDays filter when startDate is null");
  }

  @Test
  @DisplayName("endDate null → no minDays criteria")
  void endDateNull_noCriteria() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setStartDate(LocalDate.of(2025, 1, 1));

    String json = runAndCaptureQueryJson(request);

    assertFalse(json.contains(MIN_DAYS_PATH), "No minDays filter when endDate is null");
  }

  @Test
  @DisplayName("inverted range (end before start) → clamped to $lte: 1, no exception")
  void invertedRange_clampedToOne() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setStartDate(LocalDate.of(2025, 1, 10));
    request.setEndDate(LocalDate.of(2025, 1, 3));

    String json = runAndCaptureQueryJson(request);

    assertTrue(json.contains(MIN_DAYS_PATH), "Inverted range still applies the filter");
    assertTrue(json.contains("\"$lte\": 1"), "Inverted range clamps to 1, got: " + json);
  }

  @Test
  @DisplayName("filterRequest null → no minDays criteria, no NPE")
  void filterRequestNull_noCriteria() {
    stubEmpty();

    String json = runAndCaptureQueryJson(null);

    assertFalse(json.contains(MIN_DAYS_PATH), "No minDays filter when filterRequest is null");
  }

  @Test
  @DisplayName("dates + types filter coexist; base country/archived criteria preserved")
  void coexistsWithOtherFilters() {
    stubEmpty();
    BrowseInventoryRequestDTO request = baseRequest();
    request.setStartDate(LocalDate.of(2025, 1, 1));
    request.setEndDate(LocalDate.of(2025, 1, 3));
    request.setTypes(List.of("OOH"));

    String json = runAndCaptureQueryJson(request);

    assertTrue(json.contains(MIN_DAYS_PATH), "minDays filter present");
    assertTrue(json.contains("\"$lte\": 3"));
    assertTrue(json.contains("\"type\""), "types filter ($in on type) must coexist");
    assertTrue(json.contains("locationHierarchy.countryName"), "country criteria preserved");
    assertTrue(json.contains("archived"), "archived criteria preserved");
  }

  @Nested
  @DisplayName("BrowseInventoryRequestDTO.getDurationDays()")
  class DurationHelper {

    @Test
    @DisplayName("Jan 1 -> Jan 3 inclusive = 3")
    void inclusiveThreeDays() {
      BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
      request.setStartDate(LocalDate.of(2025, 1, 1));
      request.setEndDate(LocalDate.of(2025, 1, 3));
      assertEquals(3L, request.getDurationDays());
    }

    @Test
    @DisplayName("same day = 1")
    void sameDayOne() {
      BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
      request.setStartDate(LocalDate.of(2025, 1, 1));
      request.setEndDate(LocalDate.of(2025, 1, 1));
      assertEquals(1L, request.getDurationDays());
    }

    @Test
    @DisplayName("startDate null = null")
    void startNull() {
      BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
      request.setEndDate(LocalDate.of(2025, 1, 3));
      assertNull(request.getDurationDays());
    }

    @Test
    @DisplayName("endDate null = null")
    void endNull() {
      BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
      request.setStartDate(LocalDate.of(2025, 1, 1));
      assertNull(request.getDurationDays());
    }

    @Test
    @DisplayName("both null = null")
    void bothNull() {
      assertNull(new BrowseInventoryRequestDTO().getDurationDays());
    }

    @Test
    @DisplayName("inverted range clamps to 1")
    void invertedClampsToOne() {
      BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
      request.setStartDate(LocalDate.of(2025, 1, 10));
      request.setEndDate(LocalDate.of(2025, 1, 3));
      assertEquals(1L, request.getDurationDays());
    }
  }
}
