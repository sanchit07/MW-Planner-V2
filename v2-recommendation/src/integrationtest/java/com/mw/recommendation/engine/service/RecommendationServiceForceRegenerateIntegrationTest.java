package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.RunScheduleRecommendation;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import com.mw.recommendation.engine.util.RequestHashUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class RecommendationServiceForceRegenerateIntegrationTest {

  @Autowired private RecommendationService recommendationService;

  @Autowired private RecommendationRunRepository recommendationRunRepository;

  @Autowired private RecommendationResultRepository recommendationResultRepository;

  @Autowired private RunScheduleRecommendationRepository runScheduleRecommendationRepository;

  private String testCampaignId;
  private RecommendationRequestDTO testRequest;
  private final Set<String> runIdsToCleanUp = new HashSet<>();

  @BeforeEach
  void setUp() {
    testCampaignId = "test-campaign-" + UUID.randomUUID().toString().substring(0, 8);

    testRequest = new RecommendationRequestDTO();
    // Unique country so the async pipeline finds no inventories and completes quickly
    testRequest.setCountry("test-country-" + UUID.randomUUID().toString().substring(0, 8));
    testRequest.setStartDate(LocalDate.of(2025, 1, 1));
    testRequest.setEndDate(LocalDate.of(2025, 1, 31));
    testRequest.setProductId("prod-001");
    testRequest.setCompanyId("comp-001");
  }

  @AfterEach
  void tearDown() {
    for (String runId : runIdsToCleanUp) {
      recommendationResultRepository.deleteByRunId(runId);
      runScheduleRecommendationRepository.deleteByRunId(runId);
      recommendationRunRepository.findByRunId(runId).ifPresent(recommendationRunRepository::delete);
    }
    runIdsToCleanUp.clear();
  }

  private String seedCompletedRunWithChildren() {
    String runId = "test-run-" + UUID.randomUUID().toString().substring(0, 8);
    runIdsToCleanUp.add(runId);

    RecommendationRun run =
        RecommendationRun.builder()
            .runId(runId)
            .campaignId(testCampaignId)
            .productId(testRequest.getProductId())
            .companyId(testRequest.getCompanyId())
            .requestHash(RequestHashUtils.hashRequest(testRequest))
            .request(testRequest)
            .status(RecommendationRun.RunStatus.COMPLETED)
            .completionPercentage(100)
            .generatedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .warnings(new ArrayList<>())
            .build();
    recommendationRunRepository.save(run);

    recommendationResultRepository.save(
        RecommendationResult.builder()
            .runId(runId)
            .campaignId(testCampaignId)
            .inventoryId("inv-1")
            .finalScore(80.0)
            .build());
    recommendationResultRepository.save(
        RecommendationResult.builder()
            .runId(runId)
            .campaignId(testCampaignId)
            .inventoryId("inv-2")
            .finalScore(70.0)
            .build());

    runScheduleRecommendationRepository.save(
        RunScheduleRecommendation.builder()
            .runId(runId)
            .campaignId(testCampaignId)
            .inventoryId("inv-1")
            .build());

    return runId;
  }

  @Test
  void forceRegenerate_deletesOldRunAndChildren_createsNewRun_noOrphans() {
    String oldRunId = seedCompletedRunWithChildren();
    String requestHash = RequestHashUtils.hashRequest(testRequest);

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation(testCampaignId, testRequest, true);
    runIdsToCleanUp.add(response.getRunId());

    // A new run was created
    assertNotEquals(oldRunId, response.getRunId(), "Forced regeneration must create a new run");

    // Exactly one run exists for (campaignId, requestHash) — Optional lookup throws if >1
    Optional<RecommendationRun> remaining =
        recommendationRunRepository.findByCampaignIdAndRequestHash(testCampaignId, requestHash);
    assertTrue(remaining.isPresent(), "Exactly one run should remain for the payload");
    assertEquals(response.getRunId(), remaining.get().getRunId());

    // Old run and all its dependent documents are gone — no orphans
    assertTrue(recommendationRunRepository.findByRunId(oldRunId).isEmpty(), "Old run deleted");
    assertEquals(
        0,
        recommendationResultRepository.countByRunId(oldRunId),
        "No orphaned recommendation_results for old runId");
    List<RunScheduleRecommendation> orphanedSchedules =
        runScheduleRecommendationRepository.findByRunId(oldRunId);
    assertTrue(orphanedSchedules.isEmpty(), "No orphaned run_schedule_recommendations");

    // Dedup now returns the new run
    RecommendationStatusResponseDTO dedupResponse =
        recommendationService.submitRecommendation(testCampaignId, testRequest, false);
    assertEquals(response.getRunId(), dedupResponse.getRunId());
  }

  @Test
  void forceRegenerateOff_returnsExistingRunAndDeletesNothing() {
    String oldRunId = seedCompletedRunWithChildren();

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation(testCampaignId, testRequest, false);

    assertEquals(oldRunId, response.getRunId(), "Dedup must return the existing run");
    assertTrue(recommendationRunRepository.findByRunId(oldRunId).isPresent());
    assertEquals(2, recommendationResultRepository.countByRunId(oldRunId));
    assertEquals(1, runScheduleRecommendationRepository.findByRunId(oldRunId).size());
  }
}
