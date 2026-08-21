package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.scoring.AudienceFitCalculator;
import com.mw.recommendation.engine.v3.scoring.AvailabilityCalculator;
import com.mw.recommendation.engine.v3.scoring.BrandFitCalculator;
import com.mw.recommendation.engine.v3.scoring.BudgetFitCalculator;
import com.mw.recommendation.engine.v3.scoring.GeoFitCalculator;
import com.mw.recommendation.engine.v3.scoring.MeasureFitCalculator;
import com.mw.recommendation.engine.v3.scoring.QualityFitCalculator;
import com.mw.recommendation.engine.v3.scoring.TimeFitCalculator;
import com.mw.recommendation.engine.v3.scoring.V3Score;
import com.mw.recommendation.engine.v3.scoring.WeightingEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Stage 3: parallel scoring on virtual threads. The loop is DB/API-free — every calculator reads
 * only the context maps built by enrichment. Exclusions applied here per PRD: availability &lt; 10%
 * (§5.5) and, with strictBudget, cost &gt; budget (AC-05). Progress writes are throttled to the
 * configured milestone count (targeted $set, the v2 optimization).
 */
@Component
@Slf4j
public class ScoringStage {

  private final MeasureFitCalculator measureFit;
  private final GeoFitCalculator geoFit;
  private final AvailabilityCalculator availabilityCalc;
  private final BudgetFitCalculator budgetFit;
  private final AudienceFitCalculator audienceFit;
  private final BrandFitCalculator brandFit;
  private final QualityFitCalculator qualityFit;
  private final TimeFitCalculator timeFit;
  private final WeightingEngine weightingEngine;
  private final V3Properties props;
  private final RunV3Lifecycle lifecycle;
  private final TaskExecutor executor;

  public ScoringStage(
      MeasureFitCalculator measureFit,
      GeoFitCalculator geoFit,
      AvailabilityCalculator availabilityCalc,
      BudgetFitCalculator budgetFit,
      AudienceFitCalculator audienceFit,
      BrandFitCalculator brandFit,
      QualityFitCalculator qualityFit,
      TimeFitCalculator timeFit,
      WeightingEngine weightingEngine,
      V3Properties props,
      RunV3Lifecycle lifecycle,
      @Qualifier("v3TaskExecutor") TaskExecutor executor) {
    this.measureFit = measureFit;
    this.geoFit = geoFit;
    this.availabilityCalc = availabilityCalc;
    this.budgetFit = budgetFit;
    this.audienceFit = audienceFit;
    this.brandFit = brandFit;
    this.qualityFit = qualityFit;
    this.timeFit = timeFit;
    this.weightingEngine = weightingEngine;
    this.props = props;
    this.lifecycle = lifecycle;
    this.executor = executor;
  }

  public void score(V3RunContext ctx) {
    List<Inventory> candidates = ctx.getCandidates();
    if (candidates.isEmpty()) {
      ctx.setScored(List.of());
      return;
    }

    // Brand transparency (PRD §6.3): brand given but unresolvable → neutral + warning
    if (ctx.getRequest().getBrandId() != null
        && !ctx.getRequest().getBrandId().isBlank()
        && ctx.getBrandCategory() == null) {
      ctx.getWarnings()
          .warn("Brand could not be resolved to a category; brand fit scored neutral (50)");
    }

    Set<String> topRegions = topAudienceRegions(ctx);
    boolean auditEnabled = props.getAudit().isEnabled();
    boolean strictBudget = Boolean.TRUE.equals(ctx.getRequest().getStrictBudget());

    List<ScoredInventoryV3> scored =
        Collections.synchronizedList(new ArrayList<>(candidates.size()));
    AtomicInteger availabilityExcluded = new AtomicInteger();
    AtomicInteger budgetExcluded = new AtomicInteger();
    AtomicInteger unconfirmedAvailability = new AtomicInteger();
    AtomicInteger scoringErrors = new AtomicInteger();
    AtomicInteger done = new AtomicInteger();

    int total = candidates.size();
    int milestoneStep = Math.max(1, total / Math.max(1, props.getBatch().getProgressMilestones()));

    List<CompletableFuture<Void>> futures = new ArrayList<>(total);
    for (Inventory inventory : candidates) {
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  ScoredInventoryV3 result = scoreOne(inventory, ctx, topRegions, auditEnabled);

                  if (result.availability().rawPct()
                      < props.getAvailability().getExcludeBelowPct()) {
                    availabilityExcluded.incrementAndGet();
                    ctx.getWarnings().exclude("AVAILABILITY_BELOW_MINIMUM");
                    return;
                  }
                  if (strictBudget
                      && result.cost().present()
                      && ctx.getRequest().getBudget() != null
                      && result.cost().cost().compareTo(ctx.getRequest().getBudget()) > 0) {
                    budgetExcluded.incrementAndGet();
                    ctx.getWarnings().exclude("STRICT_BUDGET_EXCEEDED");
                    return;
                  }
                  if (result.availability().unconfirmed()) {
                    unconfirmedAvailability.incrementAndGet();
                  }
                  scored.add(result);
                } catch (Exception e) {
                  scoringErrors.incrementAndGet();
                  ctx.getWarnings().exclude("SCORING_ERROR");
                  log.warn(
                      "v3 scoring failed for inventory {}: {}",
                      inventory.getInventoryId(),
                      e.getMessage());
                } finally {
                  int current = done.incrementAndGet();
                  if (current % milestoneStep == 0 || current == total) {
                    lifecycle.updateProgress(ctx.getRunId(), 30 + (int) (50.0 * current / total));
                  }
                }
              },
              executor));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    if (availabilityExcluded.get() > 0) {
      ctx.getWarnings()
          .warn(
              availabilityExcluded.get()
                  + " inventories excluded: less than "
                  + (int) props.getAvailability().getExcludeBelowPct()
                  + "% of the requested schedule is available");
    }
    if (budgetExcluded.get() > 0) {
      ctx.getWarnings()
          .warn(budgetExcluded.get() + " inventories excluded by strict budget filter");
    }
    if (unconfirmedAvailability.get() > 0) {
      ctx.getWarnings()
          .warn(
              unconfirmedAvailability.get()
                  + " inventories have unconfirmed availability (no booking data; assumed fully"
                  + " available)");
    }

    ctx.setScored(new ArrayList<>(scored));
    log.info(
        "v3 scoring run {}: {} scored, {} availability-excluded, {} errors",
        ctx.getRunId(),
        scored.size(),
        availabilityExcluded.get(),
        scoringErrors.get());
  }

  private ScoredInventoryV3 scoreOne(
      Inventory inventory, V3RunContext ctx, Set<String> topRegions, boolean auditEnabled) {

    AudienceData audience = ctx.audienceFor(inventory);

    Double measure = measureFit.calculate(inventory, ctx);
    Double geo = geoFit.calculate(inventory, ctx.getRequest(), topRegions);
    AvailabilityCalculator.Result availability =
        availabilityCalc.calculate(
            inventory,
            ctx.getBookingByInventoryId().get(inventory.getInventoryId()),
            ctx.getRequest().getStartDate(),
            ctx.getRequest().getEndDate());
    BudgetFitCalculator.Result budget = budgetFit.calculate(inventory, ctx);
    Double audienceScore =
        audienceFit.calculate(inventory, audience, ctx.getRequest().getAudienceTargeting());
    Double brand =
        brandFit.calculate(inventory, ctx.getRequest().getBrandId(), ctx.getBrandCategory());
    Double quality = qualityFit.calculate(inventory);
    Double time = timeFit.calculate(audience, ctx.getRequest().getDayparts());

    V3Score score =
        weightingEngine.combine(
            measure,
            geo,
            availability.score(),
            budget.score(),
            audienceScore,
            brand,
            quality,
            time,
            auditEnabled);

    return new ScoredInventoryV3(
        inventory, score, availability, budget.estimate(), score.getFinalScore());
  }

  /**
   * Top audience-concentration cities (PRD §5.4 fallback when no geography given): city → Σ monthly
   * total visitors over the candidates, top-N by volume.
   */
  private Set<String> topAudienceRegions(V3RunContext ctx) {
    var geo = ctx.getRequest().getGeographyTargeting();
    boolean targeted =
        geo != null
            && ((geo.getCities() != null && !geo.getCities().isEmpty())
                || (geo.getStates() != null && !geo.getStates().isEmpty())
                || (geo.getGeofences() != null && !geo.getGeofences().isEmpty())
                || (geo.getPois() != null && !geo.getPois().isEmpty()));
    if (targeted) {
      return Set.of(); // targeted request — fallback never consulted
    }

    Map<String, Long> cityVisitors = new HashMap<>();
    for (Inventory inventory : ctx.getCandidates()) {
      String city =
          inventory.getLocationHierarchy() != null
              ? inventory.getLocationHierarchy().getCityName()
              : null;
      if (city == null) {
        continue;
      }
      AudienceData audience = ctx.audienceFor(inventory);
      if (audience == null
          || audience.getMonthlySummary() == null
          || audience.getMonthlySummary().getTotalVisitors() == null) {
        continue;
      }
      cityVisitors.merge(
          city.toLowerCase(Locale.ROOT),
          audience.getMonthlySummary().getTotalVisitors(),
          Long::sum);
    }
    if (cityVisitors.isEmpty()) {
      return Set.of();
    }
    Set<String> top = new HashSet<>();
    cityVisitors.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(props.getGeo().getTopPopulousRegions())
        .forEach(entry -> top.add(entry.getKey()));
    return top;
  }
}
