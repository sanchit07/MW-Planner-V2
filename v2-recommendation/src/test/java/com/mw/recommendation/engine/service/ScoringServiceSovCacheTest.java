package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.brand.lib.service.BrandService;
import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that getTotalPossibleAdPlays (used in SOV scoring) is computed once and cached in-process.
 * Before the fix, @Cacheable on a private method was silently ignored, causing a MongoDB query per
 * inventory in parallel scoring — exhausting the connection pool.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScoringService - SOV totalPossibleAdPlays cache tests")
class ScoringServiceSovCacheTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private BookingRepository bookingRepository;
  @Mock private BrandService brandService;

  @InjectMocks private ScoringServiceImpl scoringService;

  private static final LocalDate START = LocalDate.of(2025, 1, 1);
  private static final LocalDate END = LocalDate.of(2025, 1, 31);
  private static final String COUNTRY = "Japan";

  private Inventory digitalInventory;
  private Inventory marketInventory;
  private RecommendationRequestDTO sovRequest;

  @BeforeEach
  void setUp() {
    digitalInventory = buildDigitalInventory("inv-001");
    marketInventory = buildDigitalInventory("market-inv-001");
    sovRequest = buildSovRequest(COUNTRY, START, END, 5L);
  }

  @Test
  @DisplayName("findAllActiveDigitalInventoriesByCountry called once for N SOV scores — same key")
  void testDbCalledOnceForSameCountryDateRange() {
    when(inventoryRepository.findAllActiveDigitalInventoriesByCountry(COUNTRY))
        .thenReturn(List.of(marketInventory));

    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    Map<String, com.mw.brand.lib.dto.BrandResponseDTO> brandCache = new HashMap<>();

    // Score 10 inventories with SOV — all same country + date range
    for (int i = 0; i < 10; i++) {
      Inventory inv = buildDigitalInventory("inv-" + i);
      scoringService.calculateScore(inv, null, sovRequest, bookingCache, brandCache);
    }

    // DB must be called exactly once — cache served remaining 9
    verify(inventoryRepository, times(1)).findAllActiveDigitalInventoriesByCountry(COUNTRY);
  }

  @Test
  @DisplayName("Different date ranges each trigger one DB call — separate cache keys")
  void testDifferentDateRangesTriggerSeparateCalls() {
    LocalDate end2 = LocalDate.of(2025, 2, 28);
    RecommendationRequestDTO request2 = buildSovRequest(COUNTRY, START, end2, 5L);

    when(inventoryRepository.findAllActiveDigitalInventoriesByCountry(COUNTRY))
        .thenReturn(List.of(marketInventory));

    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    Map<String, com.mw.brand.lib.dto.BrandResponseDTO> brandCache = new HashMap<>();

    scoringService.calculateScore(digitalInventory, null, sovRequest, bookingCache, brandCache);
    scoringService.calculateScore(digitalInventory, null, request2, bookingCache, brandCache);

    // Two distinct keys → two DB calls
    verify(inventoryRepository, times(2)).findAllActiveDigitalInventoriesByCountry(COUNTRY);
  }

  @Test
  @DisplayName("Different countries each trigger one DB call — separate cache keys")
  void testDifferentCountriesTriggerSeparateCalls() {
    RecommendationRequestDTO japanRequest = buildSovRequest("Japan", START, END, 5L);
    RecommendationRequestDTO sgRequest = buildSovRequest("Singapore", START, END, 5L);

    when(inventoryRepository.findAllActiveDigitalInventoriesByCountry(anyString()))
        .thenReturn(List.of(marketInventory));

    Map<String, List<BookingData>> bookingCache = new HashMap<>();
    Map<String, com.mw.brand.lib.dto.BrandResponseDTO> brandCache = new HashMap<>();

    scoringService.calculateScore(digitalInventory, null, japanRequest, bookingCache, brandCache);
    scoringService.calculateScore(digitalInventory, null, sgRequest, bookingCache, brandCache);

    verify(inventoryRepository, times(1)).findAllActiveDigitalInventoriesByCountry("Japan");
    verify(inventoryRepository, times(1)).findAllActiveDigitalInventoriesByCountry("Singapore");
  }

  @Test
  @DisplayName("IMPRESSIONS goal never calls findAllActiveDigitalInventoriesByCountry")
  void testImpressionsGoalDoesNotHitDb() {
    RecommendationRequestDTO request =
        buildRequest(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    AudienceData audience = buildAudienceData();

    scoringService.calculateScore(
        digitalInventory, audience, request, new HashMap<>(), new HashMap<>());

    verify(inventoryRepository, never()).findAllActiveDigitalInventoriesByCountry(anyString());
  }

  @Test
  @DisplayName("REACH goal never calls findAllActiveDigitalInventoriesByCountry")
  void testReachGoalDoesNotHitDb() {
    RecommendationRequestDTO request = buildRequest(RecommendationRequestDTO.CampaignGoal.REACH);
    AudienceData audience = buildAudienceData();

    scoringService.calculateScore(
        digitalInventory, audience, request, new HashMap<>(), new HashMap<>());

    verify(inventoryRepository, never()).findAllActiveDigitalInventoriesByCountry(anyString());
  }

  @Test
  @DisplayName("AD_PLAYS goal never calls findAllActiveDigitalInventoriesByCountry")
  void testAdPlaysGoalDoesNotHitDb() {
    RecommendationRequestDTO request = buildRequest(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);

    scoringService.calculateScore(
        digitalInventory, null, request, new HashMap<>(), new HashMap<>());

    verify(inventoryRepository, never()).findAllActiveDigitalInventoriesByCountry(anyString());
  }

  @Test
  @DisplayName(
      "AD_PLAYS goal returns non-null measureFit for digital inventory with valid ad play data")
  void testAdPlaysMeasureFitIsNonNullForDigital() {
    RecommendationRequestDTO request = buildRequest(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);

    InventoryScore score =
        scoringService.calculateScore(
            digitalInventory, null, request, new HashMap<>(), new HashMap<>());

    assertNotNull(score.getMeasureFit(), "Digital AD_PLAYS measureFit must be non-null");
    assertTrue(
        score.getMeasureFit() >= 0.0 && score.getMeasureFit() <= 100.0,
        "measureFit must be in [0, 100]");
    assertNotNull(score.getFinalScore(), "finalScore must be computed");
    verify(inventoryRepository, never()).findAllActiveDigitalInventoriesByCountry(anyString());
  }

  @Test
  @DisplayName("AD_PLAYS goal returns null measureFit for Classic (non-digital) inventory")
  void testAdPlaysMeasureFitIsNullForClassic() {
    Inventory classic = new Inventory();
    classic.setInventoryId("classic-001");
    classic.setClassification("Classic");

    RecommendationRequestDTO request = buildRequest(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);

    InventoryScore score =
        scoringService.calculateScore(classic, null, request, new HashMap<>(), new HashMap<>());

    assertNull(
        score.getMeasureFit(), "Classic AD_PLAYS measureFit must be null — no digitalFields");
    assertNotNull(score.getFinalScore(), "finalScore still computed from redistributed weights");
  }

  @Test
  @DisplayName("SOV measureFit is null for non-digital inventory")
  void testSovMeasureFitNullForClassicInventory() {
    Inventory classic = new Inventory();
    classic.setInventoryId("classic-001");
    classic.setReferenceId("ref-classic-001");
    classic.setClassification("Classic");
    // no digitalFields

    InventoryScore score =
        scoringService.calculateScore(classic, null, sovRequest, new HashMap<>(), new HashMap<>());

    assertNull(score.getMeasureFit(), "Classic inventory has no digital fields — SOV must be null");
    verify(inventoryRepository, never()).findAllActiveDigitalInventoriesByCountry(anyString());
  }

  @Test
  @DisplayName("SOV measureFit non-null when digital inventory has valid ad play data")
  void testSovMeasureFitComputedForDigitalInventory() {
    when(inventoryRepository.findAllActiveDigitalInventoriesByCountry(COUNTRY))
        .thenReturn(List.of(marketInventory));

    InventoryScore score =
        scoringService.calculateScore(
            digitalInventory, null, sovRequest, new HashMap<>(), new HashMap<>());

    assertNotNull(
        score.getMeasureFit(),
        "Digital inventory with valid fields must produce non-null SOV measureFit");
    assertTrue(
        score.getMeasureFit() >= 0.0 && score.getMeasureFit() <= 100.0,
        "measureFit must be in [0, 100]");
  }

  // -------------------------------------------------------------------------
  // calculateRawSov tests
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("calculateRawSov returns non-null for digital inventory with valid ad play data")
  void calculateRawSov_digitalInventory_returnsNonNull() {
    when(inventoryRepository.findAllActiveDigitalInventoriesByCountry(COUNTRY))
        .thenReturn(List.of(marketInventory));

    Double rawSov = scoringService.calculateRawSov(digitalInventory, COUNTRY, START, END);

    assertNotNull(rawSov, "Digital inventory with valid fields must return non-null rawSov");
    assertTrue(rawSov >= 0.0 && rawSov <= 100.0, "rawSov must be in [0, 100]");
  }

  @Test
  @DisplayName("calculateRawSov returns null for non-digital (Classic) inventory")
  void calculateRawSov_classicInventory_returnsNull() {
    Inventory classic = new Inventory();
    classic.setInventoryId("classic-001");
    classic.setClassification("Classic");

    Double rawSov = scoringService.calculateRawSov(classic, COUNTRY, START, END);

    assertNull(rawSov, "Classic inventory has no digitalFields — rawSov must be null");
    verify(inventoryRepository, never()).findAllActiveDigitalInventoriesByCountry(anyString());
  }

  @Test
  @DisplayName("calculateRawSov returns null when digital inventory missing loopDuration")
  void calculateRawSov_digitalInventoryMissingLoopDuration_returnsNull() {
    Inventory inv = new Inventory();
    inv.setInventoryId("dig-no-loop");
    inv.setClassification("Digital");
    Inventory.DigitalFields df = new Inventory.DigitalFields();
    df.setLoopDuration(null);
    df.setSpotsPerLoop(1);
    inv.setDigitalFields(df);

    Double rawSov = scoringService.calculateRawSov(inv, COUNTRY, START, END);

    assertNull(rawSov, "Missing loopDuration must return null rawSov");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private Inventory buildDigitalInventory(String id) {
    Inventory inv = new Inventory();
    inv.setInventoryId(id);
    inv.setReferenceId("ref-" + id);
    inv.setClassification("Digital");

    Inventory.DigitalFields df = new Inventory.DigitalFields();
    df.setLoopDuration(30); // 30s loop
    df.setSpotsPerLoop(1);
    df.setSpotDuration(15);
    inv.setDigitalFields(df);

    // Operating 8h/day every day
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> times =
        new EnumMap<>(Inventory.Weekday.class);
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      times.put(day, List.of(new Inventory.OperatingTime("08:00:00", "16:00:00")));
    }
    inv.setOperatingTimes(times);

    return inv;
  }

  private AudienceData buildAudienceData() {
    AudienceData ad = new AudienceData();
    ad.setInventoryId("inv-001");
    AudienceData.MonthlySummary ms = new AudienceData.MonthlySummary();
    ms.setTotalVisitors(500000L);
    ms.setUniqueVisitors(300000L);
    ad.setMonthlySummary(ms);
    return ad;
  }

  private RecommendationRequestDTO buildSovRequest(
      String country, LocalDate start, LocalDate end, Long goalValue) {
    RecommendationRequestDTO r = new RecommendationRequestDTO();
    r.setCountry(country);
    r.setStartDate(start);
    r.setEndDate(end);
    r.setGoal(RecommendationRequestDTO.CampaignGoal.SOV);
    r.setGoalValue(goalValue);
    r.setBudget(BigDecimal.valueOf(10000));
    r.setProductId("prod-001");
    r.setCompanyId("comp-001");
    return r;
  }

  private RecommendationRequestDTO buildRequest(RecommendationRequestDTO.CampaignGoal goal) {
    RecommendationRequestDTO r = new RecommendationRequestDTO();
    r.setCountry(COUNTRY);
    r.setStartDate(START);
    r.setEndDate(END);
    r.setGoal(goal);
    r.setGoalValue(1000000L);
    r.setBudget(BigDecimal.valueOf(10000));
    r.setProductId("prod-001");
    r.setCompanyId("comp-001");
    return r;
  }
}
