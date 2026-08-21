package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import com.mw.recommendation.engine.repository.RunScheduleRecommendationRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/**
 * End-to-end equivalence check between the v1 pipeline ({@link RecommendationService}) and the
 * optimized v2 pipeline ({@link RecommendationV2Service}) against a real MongoDB.
 *
 * <p>The same request is submitted to both (under distinct campaignIds so each creates its own run
 * rather than deduping onto the other). Both runs are polled to COMPLETED and their persisted
 * {@link RecommendationResult} documents are compared: the eight component scores, cost, and
 * selectionMode must be identical per inventory, and the ranking must match. The finalScore differs
 * only by the uniform per-run jitter constant (jitter is seeded by runId), so it is compared by
 * ranking, not by absolute value.
 *
 * <p>The Measure API URL is blanked so reach/frequency enrichment is skipped deterministically —
 * removing network non-determinism. Selection-path parity under populated Measure data is covered
 * by the unit tests ({@code RecommendationAsyncServiceV2Test}), which drive identical auto-select
 * logic.
 */
@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
@TestPropertySource(properties = "mw-recommendation-engine.measure.api-url=")
@DisplayName("v1 vs v2 pipeline output equivalence (Testcontainers)")
class RecommendationV1V2EquivalenceIntegrationTest {

  @Autowired private RecommendationService recommendationService;
  @Autowired private RecommendationV2Service recommendationV2Service;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private RecommendationRunRepository recommendationRunRepository;
  @Autowired private RecommendationResultRepository recommendationResultRepository;
  @Autowired private RunScheduleRecommendationRepository runScheduleRecommendationRepository;

  private String country;
  private final Set<String> inventoryIdsToCleanUp = new HashSet<>();
  private final Set<String> runIdsToCleanUp = new HashSet<>();

  @BeforeEach
  void setUp() {
    country = "EquivTestland-" + UUID.randomUUID().toString().substring(0, 8);
    seedInventory("Digital", "OOH", 5.0);
    seedInventory("Digital", "OOH", 8.0);
    seedInventory("Classic", "OOH", 12.0);
    seedInventory("Classic", "Billboard", 20.0);
  }

  @AfterEach
  void tearDown() {
    inventoryIdsToCleanUp.forEach(inventoryRepository::deleteById);
    for (String runId : runIdsToCleanUp) {
      recommendationResultRepository.deleteByRunId(runId);
      runScheduleRecommendationRepository.deleteByRunId(runId);
      recommendationRunRepository.findByRunId(runId).ifPresent(recommendationRunRepository::delete);
    }
    inventoryIdsToCleanUp.clear();
    runIdsToCleanUp.clear();
  }

  private void seedInventory(String classification, String type, double cpm) {
    Inventory inv = new Inventory();
    inv.setInventoryId("equiv-" + UUID.randomUUID().toString().substring(0, 8));
    inv.setReferenceId("REF-" + inv.getInventoryId());
    inv.setName("Equiv " + classification + " " + type);
    inv.setArchived(false);
    inv.setClassification(classification);
    inv.setType(type);
    inv.setLocationHierarchy(Inventory.LocationHierarchy.builder().countryName(country).build());
    inv.setPrices(List.of(Inventory.PriceModel.builder().cpm(cpm).currency("USD").build()));
    // minDays small + leadDays 0 so the availability/lead-time fetch filters keep this inventory.
    inv.setSellingTerm(Inventory.SellingTerm.builder().minDays(1).leadDays(0).build());
    Inventory saved = inventoryRepository.save(inv);
    inventoryIdsToCleanUp.add(saved.getId());
  }

  private RecommendationRequestDTO buildRequest() {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry(country);
    // Future dates so lead-time is available; short duration.
    req.setStartDate(LocalDate.now().plusMonths(3));
    req.setEndDate(LocalDate.now().plusMonths(3).plusDays(9));
    req.setProductId("prod-equiv");
    req.setCompanyId("comp-equiv");
    return req;
  }

  private RecommendationRun awaitCompleted(String runId) {
    long deadline = System.currentTimeMillis() + 90_000;
    while (System.currentTimeMillis() < deadline) {
      RecommendationRun run = recommendationRunRepository.findByRunId(runId).orElse(null);
      if (run != null && run.getStatus() == RecommendationRun.RunStatus.COMPLETED) {
        return run;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    throw new AssertionError("Run " + runId + " did not reach COMPLETED within timeout");
  }

  @Test
  @DisplayName("v1 and v2 produce identical component scores, cost, selection, and ranking")
  void v1AndV2_produceEquivalentResults() {
    RecommendationRequestDTO request = buildRequest();

    // Distinct campaigns so v2 does not dedup onto v1's run (dedup key is campaignId + payload
    // hash).
    RecommendationStatusResponseDTO v1 =
        recommendationService.submitRecommendation("cmp-v1-" + country, request, false);
    RecommendationStatusResponseDTO v2 =
        recommendationV2Service.submitRecommendation("cmp-v2-" + country, request, false);
    runIdsToCleanUp.add(v1.getRunId());
    runIdsToCleanUp.add(v2.getRunId());

    awaitCompleted(v1.getRunId());
    awaitCompleted(v2.getRunId());

    List<RecommendationResult> v1Results =
        recommendationResultRepository.findByRunId(v1.getRunId());
    List<RecommendationResult> v2Results =
        recommendationResultRepository.findByRunId(v2.getRunId());

    // Sanity: the seeded inventories were actually fetched and scored by both pipelines.
    assertFalse(v1Results.isEmpty(), "v1 should score the seeded inventories");
    assertEquals(
        v1Results.size(), v2Results.size(), "v1 and v2 must produce the same number of results");

    Map<String, RecommendationResult> v1ById =
        v1Results.stream()
            .collect(Collectors.toMap(RecommendationResult::getInventoryId, Function.identity()));
    Map<String, RecommendationResult> v2ById =
        v2Results.stream()
            .collect(Collectors.toMap(RecommendationResult::getInventoryId, Function.identity()));

    assertEquals(v1ById.keySet(), v2ById.keySet(), "same set of scored inventories");

    for (String inventoryId : v1ById.keySet()) {
      RecommendationResult a = v1ById.get(inventoryId);
      RecommendationResult b = v2ById.get(inventoryId);

      assertEquals(
          a.getComponentScores(),
          b.getComponentScores(),
          "component scores must be identical for inventory " + inventoryId);
      assertEquals(
          a.getCost(), b.getCost(), "cost estimate must be identical for inventory " + inventoryId);
      assertEquals(
          a.getSelectionMode(),
          b.getSelectionMode(),
          "selectionMode must be identical for inventory " + inventoryId);
    }

    // finalScore differs only by the uniform per-run jitter constant, so the RANKING must match.
    List<String> v1Ranking = rankingByFinalScore(v1Results);
    List<String> v2Ranking = rankingByFinalScore(v2Results);
    assertEquals(
        v1Ranking, v2Ranking, "ranking by finalScore must be identical (jitter is uniform)");
  }

  private List<String> rankingByFinalScore(List<RecommendationResult> results) {
    return results.stream()
        .sorted(
            Comparator.comparing(
                    RecommendationResult::getFinalScore,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecommendationResult::getInventoryId))
        .map(RecommendationResult::getInventoryId)
        .collect(Collectors.toList());
  }
}
