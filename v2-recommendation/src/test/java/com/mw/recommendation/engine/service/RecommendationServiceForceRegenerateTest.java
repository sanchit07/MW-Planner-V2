package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService - Force Regenerate Tests")
class RecommendationServiceForceRegenerateTest {

  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationAsyncService recommendationAsyncService;
  @Mock private InventoryRepository inventoryRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private ScoringService scoringService;
  @Mock private AudienceRepository audienceRepository;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private RunScheduleRecommendationRepository runScheduleRecommendationRepository;

  @InjectMocks private RecommendationService recommendationService;

  private RecommendationRequestDTO testRequest;

  @BeforeEach
  void setUp() {
    testRequest = new RecommendationRequestDTO();
    testRequest.setCountry("Singapore");
    testRequest.setStartDate(LocalDate.of(2025, 1, 1));
    testRequest.setEndDate(LocalDate.of(2025, 1, 31));
    testRequest.setBudget(BigDecimal.valueOf(10000));
    testRequest.setProductId("prod-001");
    testRequest.setCompanyId("comp-001");
  }

  private RecommendationRun buildExistingRun(RecommendationRun.RunStatus status) {
    return RecommendationRun.builder()
        .runId("existing-run-123")
        .campaignId("campaign-001")
        .status(status)
        .completionPercentage(status == RecommendationRun.RunStatus.COMPLETED ? 100 : 50)
        .build();
  }

  @Test
  @DisplayName(
      "force=true with existing COMPLETED run deletes run and children, then creates new run")
  void forceTrue_existingCompletedRun_deletesRunAndChildrenThenCreatesNewRun() {
    RecommendationRun existingRun = buildExistingRun(RecommendationRun.RunStatus.COMPLETED);
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", testRequest, true);

    verify(recommendationRunRepository).delete(existingRun);
    verify(recommendationResultRepository).deleteByRunId("existing-run-123");
    verify(runScheduleRecommendationRepository).deleteByRunId("existing-run-123");

    // Deletion must happen before the new run is persisted
    InOrder inOrder = inOrder(recommendationRunRepository);
    inOrder.verify(recommendationRunRepository).delete(existingRun);
    inOrder.verify(recommendationRunRepository).save(any(RecommendationRun.class));

    ArgumentCaptor<RecommendationRun> runCaptor = ArgumentCaptor.forClass(RecommendationRun.class);
    verify(recommendationRunRepository).save(runCaptor.capture());
    RecommendationRun newRun = runCaptor.getValue();
    assertNotEquals("existing-run-123", newRun.getRunId(), "New run must have a new runId");
    assertEquals(RecommendationRun.RunStatus.IN_PROGRESS, newRun.getStatus());

    verify(recommendationAsyncService)
        .processRecommendationsAsync(eq(newRun.getRunId()), eq("campaign-001"), eq(testRequest));

    assertEquals(newRun.getRunId(), response.getRunId());
    assertEquals(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS, response.getStatus());
  }

  @Test
  @DisplayName("force=true with existing IN_PROGRESS run skips deletion and returns existing run")
  void forceTrue_existingInProgressRun_skipsDeletionAndReturnsExistingRun() {
    RecommendationRun existingRun = buildExistingRun(RecommendationRun.RunStatus.IN_PROGRESS);
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", testRequest, true);

    assertEquals("existing-run-123", response.getRunId());
    assertEquals(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS, response.getStatus());

    verify(recommendationRunRepository, never()).delete(any());
    verify(recommendationResultRepository, never()).deleteByRunId(anyString());
    verify(runScheduleRecommendationRepository, never()).deleteByRunId(anyString());
    verify(recommendationRunRepository, never()).save(any());
    verify(recommendationAsyncService, never())
        .processRecommendationsAsync(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("force=true with no existing run creates new run without any deletes")
  void forceTrue_noExistingRun_createsNewRunWithoutDeletes() {
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", testRequest, true);

    verify(recommendationRunRepository, never()).delete(any());
    verify(recommendationResultRepository, never()).deleteByRunId(anyString());
    verify(runScheduleRecommendationRepository, never()).deleteByRunId(anyString());

    verify(recommendationRunRepository).save(any(RecommendationRun.class));
    verify(recommendationAsyncService)
        .processRecommendationsAsync(eq(response.getRunId()), eq("campaign-001"), eq(testRequest));
  }

  @Test
  @DisplayName("force=false with existing run returns existing run and never deletes")
  void forceFalse_existingRun_returnsExistingRunAndNeverDeletes() {
    RecommendationRun existingRun = buildExistingRun(RecommendationRun.RunStatus.COMPLETED);
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", testRequest, false);

    assertEquals("existing-run-123", response.getRunId());

    verify(recommendationRunRepository, never()).delete(any());
    verify(recommendationResultRepository, never()).deleteByRunId(anyString());
    verify(runScheduleRecommendationRepository, never()).deleteByRunId(anyString());
    verify(recommendationRunRepository, never()).save(any());
    verify(recommendationAsyncService, never())
        .processRecommendationsAsync(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("Two-arg overload delegates with force=false, preserving dedup contract")
  void twoArgOverload_delegatesWithForceFalse() {
    RecommendationRun existingRun = buildExistingRun(RecommendationRun.RunStatus.COMPLETED);
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", testRequest);

    assertEquals("existing-run-123", response.getRunId());

    verify(recommendationRunRepository, never()).delete(any());
    verify(recommendationResultRepository, never()).deleteByRunId(anyString());
    verify(runScheduleRecommendationRepository, never()).deleteByRunId(anyString());
    verify(recommendationRunRepository, never()).save(any());
    verify(recommendationAsyncService, never())
        .processRecommendationsAsync(anyString(), anyString(), any());
  }
}
