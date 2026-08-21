package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.domain.RecommendationResultV3;
import com.mw.recommendation.engine.v3.domain.RunScheduleV3;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.support.StageTimer;
import com.mw.recommendation.engine.v3.support.WarningCollector;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Mutable state threaded through the v3 pipeline stages. One instance per run; stages read the
 * fields earlier stages populated and never re-query for them (the no-N+1 invariant).
 */
@Getter
@Setter
public class V3RunContext {

  private final String runId;
  private final String campaignId;
  private final RecommendationV3RequestDTO request;
  private final String seed;
  private final WarningCollector warnings = new WarningCollector();
  private final StageTimer timer;

  /** Candidates surviving the fetch stage. */
  private List<Inventory> candidates = List.of();

  private int fetchedCount;

  // ── Prefetched data (EnrichmentStage) — the only DB reads the hot path sees ──
  private Map<String, AudienceData> audienceByInventoryId = Map.of();
  private Map<String, AudienceData> audienceByReferenceId = Map.of();
  private Map<String, List<BookingData>> bookingByInventoryId = Map.of();

  /** Resolved brand IAB category (null when brand absent, invalid, or lookup failed). */
  private String brandCategory;

  /** referenceId (or inventoryId when referenceId is absent) → Measure metrics. */
  private Map<String, MeasureData> measureByReferenceId = Map.of();

  public MeasureData measureFor(Inventory inventory) {
    MeasureData byRef =
        inventory.getReferenceId() != null
            ? measureByReferenceId.get(inventory.getReferenceId())
            : null;
    return byRef != null ? byRef : measureByReferenceId.get(inventory.getInventoryId());
  }

  /** Scored + jittered inventories, sorted descending, after topN. */
  private List<ScoredInventoryV3> scored = List.of();

  /** Built result documents, selectionMode stamped before insert. */
  private List<RecommendationResultV3> results = List.of();

  private List<RunScheduleV3> schedules = List.of();
  private List<String> autoSelectedInventoryIds = List.of();

  /** inventoryId → budgeted spend chosen by selection (schedule stage prices precisely). */
  private Map<String, java.math.BigDecimal> selectedSpend = Map.of();

  public V3RunContext(
      String runId, String campaignId, RecommendationV3RequestDTO request, String seed) {
    this.runId = runId;
    this.campaignId = campaignId;
    this.request = request;
    this.seed = seed;
    this.timer = new StageTimer("v3|runId=" + runId);
  }

  /** Inclusive campaign length in days. */
  public long campaignDays() {
    return Math.max(1, ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1);
  }

  public AudienceData audienceFor(Inventory inventory) {
    AudienceData byId = audienceByInventoryId.get(inventory.getInventoryId());
    if (byId != null) {
      return byId;
    }
    return inventory.getReferenceId() != null
        ? audienceByReferenceId.get(inventory.getReferenceId())
        : null;
  }

  /** Per-inventory Measure metrics (impressions/reach) with provenance. */
  public record MeasureData(Long impressions, Long reach, String source) {
    public boolean valid() {
      return impressions != null && impressions > 0 && reach != null && reach > 0;
    }
  }
}
