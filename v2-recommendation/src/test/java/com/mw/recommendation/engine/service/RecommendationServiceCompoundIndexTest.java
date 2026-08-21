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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Integration tests for RecommendationService focusing on the compound index optimization for
 * deduplication. Tests verify that findByCampaignIdAndRequestHash query benefits from the compound
 * index.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService - Compound Index Optimization Tests")
class RecommendationServiceCompoundIndexTest {

  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationAsyncService recommendationAsyncService;
  @Mock private InventoryRepository inventoryRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private ScoringService scoringService;
  @Mock private AudienceRepository audienceRepository;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;

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

  @Test
  @DisplayName("Should use compound index query (findByCampaignIdAndRequestHash) for deduplication")
  void testUsesCompoundIndexQueryForDeduplication() {
    // Arrange: No existing run
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Act
    recommendationService.submitRecommendation("campaign-001", testRequest);

    // Assert: Verify compound index query was called
    ArgumentCaptor<String> campaignIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> requestHashCaptor = ArgumentCaptor.forClass(String.class);

    verify(recommendationRunRepository, times(1))
        .findByCampaignIdAndRequestHash(campaignIdCaptor.capture(), requestHashCaptor.capture());

    assertEquals("campaign-001", campaignIdCaptor.getValue(), "Should query by campaignId");
    assertNotNull(requestHashCaptor.getValue(), "Should query by requestHash");
    assertFalse(requestHashCaptor.getValue().isEmpty(), "RequestHash should be non-empty string");

    // Verify async processing was triggered for new run
    verify(recommendationAsyncService, times(1))
        .processRecommendationsAsync(anyString(), eq("campaign-001"), eq(testRequest));
  }

  @Test
  @DisplayName(
      "Should return existing runId when duplicate request detected (using compound index)")
  void testReturnsExistingRunIdForDuplicateRequest() {
    // Arrange: Existing run with same campaignId and requestHash
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("existing-run-123")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.COMPLETED)
            .completionPercentage(100)
            .build();

    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    // Act
    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", testRequest);

    // Assert: Should return existing runId
    assertEquals("existing-run-123", response.getRunId(), "Should return existing runId");
    assertEquals(
        RecommendationStatusResponseDTO.RunStatus.COMPLETED,
        response.getStatus(),
        "Should return COMPLETED status");

    // Verify async processing was NOT triggered (duplicate detected)
    verify(recommendationAsyncService, never())
        .processRecommendationsAsync(anyString(), anyString(), any());

    // Verify no new run was saved
    verify(recommendationRunRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should generate different requestHash for different request parameters")
  void testGeneratesDifferentHashForDifferentRequests() {
    // Arrange: Two different requests
    RecommendationRequestDTO request1 = new RecommendationRequestDTO();
    request1.setCountry("Singapore");
    request1.setStartDate(LocalDate.of(2025, 1, 1));
    request1.setEndDate(LocalDate.of(2025, 1, 31));
    request1.setBudget(BigDecimal.valueOf(10000));
    request1.setProductId("prod-001");
    request1.setCompanyId("comp-001");

    RecommendationRequestDTO request2 = new RecommendationRequestDTO();
    request2.setCountry("Singapore");
    request2.setStartDate(LocalDate.of(2025, 2, 1)); // Different dates
    request2.setEndDate(LocalDate.of(2025, 2, 28));
    request2.setBudget(BigDecimal.valueOf(10000));
    request2.setProductId("prod-001");
    request2.setCompanyId("comp-001");

    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Act: Submit both requests
    recommendationService.submitRecommendation("campaign-001", request1);
    recommendationService.submitRecommendation("campaign-001", request2);

    // Assert: Verify compound index query called twice with different hashes
    ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
    verify(recommendationRunRepository, times(2))
        .findByCampaignIdAndRequestHash(eq("campaign-001"), hashCaptor.capture());

    List<String> capturedHashes = hashCaptor.getAllValues();
    assertEquals(2, capturedHashes.size(), "Should have captured 2 hashes");
    assertNotEquals(
        capturedHashes.get(0),
        capturedHashes.get(1),
        "Different requests should produce different hashes");
  }

  @Test
  @DisplayName("Should generate same requestHash for identical requests (idempotent)")
  void testGeneratesSameHashForIdenticalRequests() {
    // Arrange: Submit same request twice
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(RecommendationRun.builder().runId("run-123").build()));
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Act: Submit same request twice
    recommendationService.submitRecommendation("campaign-001", testRequest);
    recommendationService.submitRecommendation("campaign-001", testRequest);

    // Assert: Verify same hash used both times
    ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
    verify(recommendationRunRepository, times(2))
        .findByCampaignIdAndRequestHash(eq("campaign-001"), hashCaptor.capture());

    List<String> capturedHashes = hashCaptor.getAllValues();
    assertEquals(2, capturedHashes.size(), "Should have captured 2 hashes");
    assertEquals(
        capturedHashes.get(0),
        capturedHashes.get(1),
        "Identical requests should produce identical hashes");
  }

  @Test
  @DisplayName("Should use compound index for multiple campaigns efficiently")
  void testCompoundIndexWorksForMultipleCampaigns() {
    // Arrange: Multiple campaigns with different requests
    String[] campaigns = {"campaign-001", "campaign-002", "campaign-003"};

    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Act: Submit requests for different campaigns
    for (String campaign : campaigns) {
      recommendationService.submitRecommendation(campaign, testRequest);
    }

    // Assert: Verify compound index query used for each campaign
    ArgumentCaptor<String> campaignCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);

    verify(recommendationRunRepository, times(3))
        .findByCampaignIdAndRequestHash(campaignCaptor.capture(), hashCaptor.capture());

    List<String> capturedCampaigns = campaignCaptor.getAllValues();
    assertEquals(3, capturedCampaigns.size(), "Should query for 3 campaigns");
    assertTrue(capturedCampaigns.contains("campaign-001"), "Should query campaign-001");
    assertTrue(capturedCampaigns.contains("campaign-002"), "Should query campaign-002");
    assertTrue(capturedCampaigns.contains("campaign-003"), "Should query campaign-003");

    // All should have same hash (same request parameters)
    List<String> capturedHashes = hashCaptor.getAllValues();
    assertEquals(
        capturedHashes.get(0), capturedHashes.get(1), "Same request should produce same hash");
    assertEquals(
        capturedHashes.get(1), capturedHashes.get(2), "Same request should produce same hash");
  }

  @Test
  @DisplayName("Should handle concurrent duplicate submissions efficiently with compound index")
  void testHandlesConcurrentDuplicateSubmissions() {
    // Arrange: Simulate concurrent submissions
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("run-concurrent-123")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.IN_PROGRESS)
            .completionPercentage(50)
            .build();

    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    // Act: Submit multiple times (simulating concurrent requests)
    for (int i = 0; i < 5; i++) {
      RecommendationStatusResponseDTO response =
          recommendationService.submitRecommendation("campaign-001", testRequest);

      // Assert: All should return same runId
      assertEquals(
          "run-concurrent-123",
          response.getRunId(),
          "All concurrent requests should get same runId");
    }

    // Assert: Compound index query called 5 times
    verify(recommendationRunRepository, times(5))
        .findByCampaignIdAndRequestHash(eq("campaign-001"), anyString());

    // Assert: No new runs created, no async processing triggered
    verify(recommendationRunRepository, never()).save(any());
    verify(recommendationAsyncService, never())
        .processRecommendationsAsync(anyString(), anyString(), any());
  }
}
