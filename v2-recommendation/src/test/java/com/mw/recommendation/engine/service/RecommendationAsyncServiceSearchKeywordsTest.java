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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationAsyncService - searchKeywords propagation to repository")
class RecommendationAsyncServiceSearchKeywordsTest {

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

  @Spy
  private java.time.Clock clock =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC);

  @InjectMocks private RecommendationAsyncService service;

  private static final String RUN_ID = "run-keywords-test-001";
  private static final String CAMPAIGN_ID = "campaign-keywords-001";

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
    req.setStartDate(LocalDate.of(2026, 7, 1));
    req.setEndDate(LocalDate.of(2026, 7, 31));
    return req;
  }

  @Test
  @DisplayName("searchKeywords in request → passed to repository as-is")
  void searchKeywordsArePassedToRepository() {
    RecommendationRequestDTO req = buildRequest();
    req.setSearchKeywords(List.of("Kuala Lumpur", "Cyberjaya"));
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            anyString(),
            any(),
            any(),
            any(),
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

    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            eq("MY"),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(List.of("Kuala Lumpur", "Cyberjaya")),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());
  }

  @Test
  @DisplayName("null searchKeywords → null passed to repository, no behavior change")
  void nullSearchKeywordsPassedAsNull_noBehaviorChange() {
    RecommendationRequestDTO req = buildRequest();
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            isNull(),
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

    verify(inventoryRepository)
        .findActiveInventoriesByCountryWithGeographyTargeting(
            eq("MY"),
            any(),
            any(),
            any(),
            any(),
            any(),
            isNull(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any());
  }

  @Test
  @DisplayName("zero matches → run COMPLETED with 0 results and SEARCH_KEYWORDS_FILTER reason")
  void zeroMatchesCompletesRunWithEmptyResultsAndExclusionReason() {
    RecommendationRequestDTO req = buildRequest();
    req.setSearchKeywords(List.of("NoSuchPlace"));
    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            anyString(),
            any(),
            any(),
            any(),
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

    ArgumentCaptor<RecommendationRun> captor = ArgumentCaptor.forClass(RecommendationRun.class);
    verify(recommendationRunRepository, atLeastOnce()).save(captor.capture());
    RecommendationRun saved = captor.getValue();
    assertEquals(RecommendationRun.RunStatus.COMPLETED, saved.getStatus());
    assertEquals(0, saved.getMetadata().getTotalInventoriesRecommended());
    assertEquals(1, saved.getMetadata().getExclusionReasons().get("SEARCH_KEYWORDS_FILTER"));
  }
}
