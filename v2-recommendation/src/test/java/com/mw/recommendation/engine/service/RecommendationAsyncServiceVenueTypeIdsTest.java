package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationAsyncService - venueTypeIds propagation to repository")
class RecommendationAsyncServiceVenueTypeIdsTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private AudienceRepository audienceRepository;
  @Mock private ScoringService scoringService;
  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private VirtualThreadTaskExecutor virtualThreadTaskExecutor;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private MeasureApiClient measureApiClient;

  @Mock private AutoSelectionReasonResolver autoSelectionReasonResolver;

  @org.mockito.Spy
  private java.time.Clock clock =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC);

  @InjectMocks private RecommendationAsyncService service;

  private static final String RUN_ID = "run-venue-test-001";
  private static final String CAMPAIGN_ID = "campaign-venue-001";

  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            invocation -> {
              Runnable r = invocation.getArgument(0);
              r.run();
              return null;
            })
        .when(virtualThreadTaskExecutor)
        .execute(any());

    RecommendationRun run = new RecommendationRun();
    run.setRunId(RUN_ID);
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    lenient().when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(run));
    lenient().when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(mongoTemplate.insert(anyList(), any(Class.class)))
        .thenAnswer(i -> i.getArgument(0));
    lenient()
        .doNothing()
        .when(recommendationResultRepository)
        .bulkUpdateSelectionMode(anyString(), anyMap());
    lenient()
        .when(
            scheduleRecommendationService.buildBestScheduleForBudgetCap(
                any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    lenient()
        .when(audienceRepository.findByInventoryIdInOrReferenceIdIn(anyList(), anyList()))
        .thenReturn(List.of());
    lenient()
        .when(scoringService.batchFetchBookingData(anyList(), any(), any()))
        .thenReturn(new HashMap<>());
    lenient().when(scoringService.batchFetchBrandData(anyList())).thenReturn(new HashMap<>());
  }

  private RecommendationRequestDTO buildRequest() {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("MY");
    req.setStartDate(LocalDate.of(2025, 3, 1));
    req.setEndDate(LocalDate.of(2025, 5, 31));
    req.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    req.setGoalValue(2_000_000L);
    return req;
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("venueTypeIds in audienceTargeting → passed to repository")
  void venueTypeIds_inAudienceTargeting_passedToRepository() {
    RecommendationRequestDTO req = buildRequest();
    RecommendationRequestDTO.AudienceTargeting targeting =
        new RecommendationRequestDTO.AudienceTargeting();
    targeting.setVenueTypeIds(Map.of("digital", List.of("401", "402"), "classic", List.of("301")));
    req.setAudienceTargeting(targeting);

    ArgumentCaptor<Map<String, List<String>>> venueCaptor = ArgumentCaptor.forClass(Map.class);
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            venueCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any()))
        .thenReturn(List.of());

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, req);

    Map<String, List<String>> captured = venueCaptor.getValue();
    assertNotNull(captured);
    assertEquals(List.of("401", "402"), captured.get("digital"));
    assertEquals(List.of("301"), captured.get("classic"));
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("null audienceTargeting → null venueTypeIds passed to repository")
  void nullAudienceTargeting_passesNullVenueTypeIdsToRepository() {
    RecommendationRequestDTO req = buildRequest();
    req.setAudienceTargeting(null);

    ArgumentCaptor<Map<String, List<String>>> venueCaptor = ArgumentCaptor.forClass(Map.class);
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            venueCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any()))
        .thenReturn(List.of());

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, req);

    assertNull(venueCaptor.getValue());
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("audienceTargeting with null venueTypeIds → null passed to repository")
  void audienceTargetingWithNullVenueTypeIds_passesNullToRepository() {
    RecommendationRequestDTO req = buildRequest();
    RecommendationRequestDTO.AudienceTargeting targeting =
        new RecommendationRequestDTO.AudienceTargeting();
    targeting.setVenueTypeIds(null);
    req.setAudienceTargeting(targeting);

    ArgumentCaptor<Map<String, List<String>>> venueCaptor = ArgumentCaptor.forClass(Map.class);
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            venueCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any()))
        .thenReturn(List.of());

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, req);

    assertNull(venueCaptor.getValue());
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("venueTypeIds demographics field is separate — no venues key in demographics")
  void demographics_doesNotContainVenuesKey() {
    RecommendationRequestDTO req = buildRequest();
    RecommendationRequestDTO.AudienceTargeting targeting =
        new RecommendationRequestDTO.AudienceTargeting();
    targeting.setDemographics(Map.of("age", List.of("18-24"), "gender", List.of("MALE")));
    targeting.setVenueTypeIds(Map.of("digital", List.of("401")));
    req.setAudienceTargeting(targeting);

    ArgumentCaptor<Map<String, List<String>>> venueCaptor = ArgumentCaptor.forClass(Map.class);
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            any(),
            any(),
            any(),
            venueCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any()))
        .thenReturn(List.of());

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, req);

    Map<String, List<String>> captured = venueCaptor.getValue();
    assertNotNull(captured);
    assertEquals(List.of("401"), captured.get("digital"));
    // demographics.venues no longer drives venue filtering
    assertFalse(captured.containsKey("age"));
    assertFalse(captured.containsKey("gender"));
  }
}
