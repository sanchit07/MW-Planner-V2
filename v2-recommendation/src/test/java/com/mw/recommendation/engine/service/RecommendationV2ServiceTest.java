package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Behavioral tests for the v2 sync entry point. It must mirror v1's dedup / forceRegenerate
 * semantics exactly, but trigger the v2 async pipeline ({@link RecommendationAsyncServiceV2})
 * rather than v1's.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationV2ServiceTest {

  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private RunScheduleRecommendationRepository runScheduleRecommendationRepository;
  @Mock private RecommendationAsyncServiceV2 recommendationAsyncServiceV2;

  @Mock private org.springframework.core.task.VirtualThreadTaskExecutor virtualThreadTaskExecutor;

  @InjectMocks private RecommendationV2Service recommendationV2Service;

  private RecommendationRequestDTO testRequest;

  @BeforeEach
  void setUp() {
    // Run the async dependent-cleanup (finding #13) inline so tests can assert its effects.
    lenient()
        .doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            })
        .when(virtualThreadTaskExecutor)
        .execute(any());

    testRequest = new RecommendationRequestDTO();
    testRequest.setCountry("Singapore");
    testRequest.setStartDate(LocalDate.of(2025, 1, 1));
    testRequest.setEndDate(LocalDate.of(2025, 1, 31));
    testRequest.setBudget(BigDecimal.valueOf(10000));
    testRequest.setProductId("prod-001");
    testRequest.setCompanyId("comp-001");
  }

  @Test
  void newRequest_createsRunAndTriggersV2Async() {
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    RecommendationStatusResponseDTO response =
        recommendationV2Service.submitRecommendation("campaign-001", testRequest, false);

    assertNotNull(response.getRunId());
    assertEquals(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS, response.getStatus());
    assertEquals(0, response.getCompletionPercentage());
    assertEquals("campaign-001", response.getCampaignId());

    ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
    verify(recommendationRunRepository)
        .findByCampaignIdAndRequestHash(eq("campaign-001"), hashCaptor.capture());
    assertNotNull(hashCaptor.getValue());
    assertFalse(hashCaptor.getValue().isEmpty());

    verify(recommendationRunRepository).save(any(RecommendationRun.class));
    verify(recommendationAsyncServiceV2)
        .processRecommendationsAsyncOptimized(anyString(), eq("campaign-001"), eq(testRequest));
  }

  @Test
  void duplicateRequest_statusResponse_exposesAutoSelectionReason() {
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("existing-run-123")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.COMPLETED)
            .completionPercentage(100)
            .autoSelectionReasonCode(
                com.mw.recommendation.engine.domain.AutoSelectionReasonCode.BUDGET_TOO_LOW)
            .autoSelectionReasonDetail("cheapest candidate estimated cost 10000 exceeds budget 50")
            .build();
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationV2Service.submitRecommendation("campaign-001", testRequest, false);

    assertEquals("BUDGET_TOO_LOW", response.getAutoSelectionReasonCode());
    assertEquals(
        "cheapest candidate estimated cost 10000 exceeds budget 50",
        response.getAutoSelectionReasonDetail());
  }

  @Test
  void duplicateRequest_returnsExistingRun_noSave_noAsync() {
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("existing-run-123")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.COMPLETED)
            .completionPercentage(100)
            .build();
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationV2Service.submitRecommendation("campaign-001", testRequest, false);

    assertEquals("existing-run-123", response.getRunId());
    assertEquals(RecommendationStatusResponseDTO.RunStatus.COMPLETED, response.getStatus());

    verify(recommendationRunRepository, never()).save(any());
    verify(recommendationAsyncServiceV2, never())
        .processRecommendationsAsyncOptimized(anyString(), anyString(), any());
  }

  @Test
  void forceRegenerate_completedRun_deletesAndRegenerates() {
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("old-run-999")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.COMPLETED)
            .completionPercentage(100)
            .build();
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    RecommendationStatusResponseDTO response =
        recommendationV2Service.submitRecommendation("campaign-001", testRequest, true);

    assertNotEquals("old-run-999", response.getRunId());
    verify(recommendationRunRepository).delete(existingRun);
    verify(recommendationResultRepository).deleteByRunId("old-run-999");
    verify(runScheduleRecommendationRepository).deleteByRunId("old-run-999");
    verify(recommendationRunRepository).save(any(RecommendationRun.class));
    verify(recommendationAsyncServiceV2)
        .processRecommendationsAsyncOptimized(anyString(), eq("campaign-001"), eq(testRequest));
  }

  @Test
  void failedRun_withoutForceRegenerate_regenerates() {
    // Finding #1: a FAILED run is not a usable cached result, so it must regenerate even without
    // forceRegenerate — otherwise a transient failure is served back to the client forever.
    RecommendationRun failedRun =
        RecommendationRun.builder()
            .runId("failed-run-1")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.FAILED)
            .completionPercentage(0)
            .build();
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(failedRun));
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    RecommendationStatusResponseDTO response =
        recommendationV2Service.submitRecommendation("campaign-001", testRequest, false);

    assertNotEquals("failed-run-1", response.getRunId());
    assertEquals(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS, response.getStatus());
    verify(recommendationRunRepository).delete(failedRun);
    verify(recommendationResultRepository).deleteByRunId("failed-run-1");
    verify(runScheduleRecommendationRepository).deleteByRunId("failed-run-1");
    verify(recommendationRunRepository).save(any(RecommendationRun.class));
    verify(recommendationAsyncServiceV2)
        .processRecommendationsAsyncOptimized(anyString(), eq("campaign-001"), eq(testRequest));
  }

  @Test
  void forceRegenerate_inProgressRun_returnsExisting_noDelete_noAsync() {
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("inprogress-run-1")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.IN_PROGRESS)
            .completionPercentage(50)
            .build();
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationV2Service.submitRecommendation("campaign-001", testRequest, true);

    assertEquals("inprogress-run-1", response.getRunId());
    verify(recommendationRunRepository, never()).delete(any());
    verify(recommendationRunRepository, never()).save(any());
    verify(recommendationAsyncServiceV2, never())
        .processRecommendationsAsyncOptimized(anyString(), anyString(), any());
  }
}
