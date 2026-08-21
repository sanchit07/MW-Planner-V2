package com.mw.recommendation.engine.v3.pipeline;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.measure.MeasureInventoryDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyRequestDTO;
import com.mw.recommendation.engine.dto.measure.MeasureReachFrequencyResponseDTO;
import com.mw.recommendation.engine.v3.config.V3Properties;
import com.mw.recommendation.engine.v3.support.BoundedExecutor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Stage 2: batch prefetch of everything the scoring hot loop needs — after this stage the pipeline
 * performs zero per-inventory DB or API calls. Audience is fetched with a single $or query per
 * batch covering BOTH inventoryId and referenceId (the referenceId path is silently disabled in
 * v1/v2 — weakness W6). The Measure fallback chain implements PRD §3.3: Measure → derived from
 * audience data (flagged) → exclude with warning (AC-03/AC-04).
 */
@Component
@Slf4j
public class EnrichmentStage {

  private final MongoTemplate mongoTemplate;
  private final MeasureV3Client measureClient;
  private final V3Properties props;
  private final BoundedExecutor dbExecutor;
  private final BoundedExecutor measureExecutor;

  public EnrichmentStage(
      MongoTemplate mongoTemplate,
      MeasureV3Client measureClient,
      V3Properties props,
      @Qualifier("v3DbExecutor") BoundedExecutor dbExecutor,
      @Qualifier("v3MeasureExecutor") BoundedExecutor measureExecutor) {
    this.mongoTemplate = mongoTemplate;
    this.measureClient = measureClient;
    this.props = props;
    this.dbExecutor = dbExecutor;
    this.measureExecutor = measureExecutor;
  }

  public void enrich(V3RunContext ctx) {
    List<Inventory> candidates = ctx.getCandidates();
    if (candidates.isEmpty()) {
      return;
    }

    prefetchAudience(ctx, candidates);
    prefetchBooking(ctx, candidates);
    fetchMeasure(ctx, candidates);
    applyMeasureFallbackChain(ctx);
  }

  /** One $or query per batch over inventoryId AND referenceId, gated by the DB semaphore. */
  private void prefetchAudience(V3RunContext ctx, List<Inventory> candidates) {
    int batchSize = props.getBatch().getAudienceBatchSize();
    List<List<Inventory>> batches = partition(candidates, batchSize);

    List<Supplier<List<AudienceData>>> tasks = new ArrayList<>(batches.size());
    for (List<Inventory> batch : batches) {
      List<String> ids =
          batch.stream().map(Inventory::getInventoryId).filter(Objects::nonNull).toList();
      List<String> refIds =
          batch.stream().map(Inventory::getReferenceId).filter(Objects::nonNull).toList();
      tasks.add(
          () ->
              mongoTemplate.find(
                  new Query(
                      new Criteria()
                          .orOperator(
                              Criteria.where("inventoryId").in(ids),
                              Criteria.where("referenceId").in(refIds))),
                  AudienceData.class));
    }

    Map<String, AudienceData> byInventoryId = new HashMap<>();
    Map<String, AudienceData> byReferenceId = new HashMap<>();
    for (List<AudienceData> batchResult : dbExecutor.invokeAll(tasks)) {
      for (AudienceData data : batchResult) {
        if (data.getInventoryId() != null) {
          byInventoryId.putIfAbsent(data.getInventoryId(), data);
        }
        if (data.getReferenceId() != null) {
          byReferenceId.putIfAbsent(data.getReferenceId(), data);
        }
      }
    }
    ctx.setAudienceByInventoryId(byInventoryId);
    ctx.setAudienceByReferenceId(byReferenceId);
    log.info(
        "v3 audience prefetch run {}: {} docs for {} candidates",
        ctx.getRunId(),
        byInventoryId.size(),
        candidates.size());
  }

  /** Single date-ranged query for all candidates (booking_data is one doc per inventory+date). */
  private void prefetchBooking(V3RunContext ctx, List<Inventory> candidates) {
    List<String> ids =
        candidates.stream().map(Inventory::getInventoryId).filter(Objects::nonNull).toList();
    List<BookingData> bookings =
        mongoTemplate.find(
            new Query(
                Criteria.where("inventoryId")
                    .in(ids)
                    .and("date")
                    .gte(ctx.getRequest().getStartDate())
                    .lte(ctx.getRequest().getEndDate())),
            BookingData.class);
    ctx.setBookingByInventoryId(
        bookings.stream().collect(Collectors.groupingBy(BookingData::getInventoryId)));
  }

  /** Parallel batched Measure calls (bounded), one aggregate request per batch. */
  private void fetchMeasure(V3RunContext ctx, List<Inventory> candidates) {
    int batchSize = props.getBatch().getMeasureBatchSize();
    List<List<Inventory>> batches = partition(candidates, batchSize);
    int duration = (int) ctx.campaignDays();

    List<Supplier<List<MeasureReachFrequencyResponseDTO>>> tasks = new ArrayList<>(batches.size());
    for (List<Inventory> batch : batches) {
      List<MeasureInventoryDTO> inventories =
          batch.stream().map(inv -> toMeasureInventory(inv, ctx)).toList();
      MeasureReachFrequencyRequestDTO request =
          MeasureReachFrequencyRequestDTO.builder()
              .inventories(inventories)
              .duration(duration)
              .build();
      tasks.add(() -> measureClient.getReachAndFrequency(request, true));
    }

    Map<String, V3RunContext.MeasureData> measureMap = new ConcurrentHashMap<>();
    for (List<MeasureReachFrequencyResponseDTO> batchResult : measureExecutor.invokeAll(tasks)) {
      for (MeasureReachFrequencyResponseDTO row : batchResult) {
        if (row.getReferenceId() != null
            && (row.getStatus() == null || "success".equalsIgnoreCase(row.getStatus()))) {
          measureMap.put(
              row.getReferenceId(),
              new V3RunContext.MeasureData(row.getImpressions(), row.getReach(), "measure"));
        }
      }
    }
    ctx.setMeasureByReferenceId(new HashMap<>(measureMap));
  }

  private MeasureInventoryDTO toMeasureInventory(Inventory inv, V3RunContext ctx) {
    MeasureInventoryDTO.MeasureInventoryDTOBuilder builder =
        MeasureInventoryDTO.builder()
            .referenceId(inv.getReferenceId() != null ? inv.getReferenceId() : inv.getInventoryId())
            .type(inv.getClassification())
            .spotsPerHour(spotsPerHour(inv));

    // PRD §5.10 accuracy flag: attach requested dayparts (off by default = v2 parity)
    if (props.getMeasure().isIncludeDayparts()
        && ctx.getRequest().getDayparts() != null
        && !ctx.getRequest().getDayparts().isEmpty()) {
      List<String> hours =
          ctx.getRequest().getDayparts().stream()
              .filter(d -> d.getStartHour() != null && d.getEndHour() != null)
              .flatMap(
                  d ->
                      java.util.stream.IntStream.range(d.getStartHour(), d.getEndHour())
                          .mapToObj(h -> String.format("%02d:00", h)))
              .distinct()
              .sorted()
              .toList();
      if (!hours.isEmpty()) {
        List<MeasureInventoryDTO.Dayparts> dayparts = new ArrayList<>();
        LocalDate date = ctx.getRequest().getStartDate();
        while (!date.isAfter(ctx.getRequest().getEndDate())) {
          dayparts.add(
              MeasureInventoryDTO.Dayparts.builder()
                  .scheduledDate(date.toString())
                  .scheduledTime(hours)
                  .build());
          date = date.plusDays(1);
        }
        builder.dayparts(dayparts);
      }
    }
    return builder.build();
  }

  private static Integer spotsPerHour(Inventory inv) {
    Inventory.DigitalFields digital = inv.getDigitalFields();
    if (digital == null) {
      return null;
    }
    if (digital.getLoopsPerHour() != null && digital.getLoopsPerHour() > 0) {
      return digital.getLoopsPerHour();
    }
    if (digital.getLoopDuration() != null && digital.getLoopDuration() > 0) {
      return 3600 / digital.getLoopDuration();
    }
    return null;
  }

  /**
   * PRD §3.3 fallback chain: Measure → derived (adPlays-independent audience estimate, flagged) →
   * exclude with warning (AC-04). Runs after the Measure fetch so the map holds every candidate's
   * best-available metrics with provenance.
   */
  private void applyMeasureFallbackChain(V3RunContext ctx) {
    Map<String, V3RunContext.MeasureData> measureMap = new HashMap<>(ctx.getMeasureByReferenceId());
    List<Inventory> retained = new ArrayList<>(ctx.getCandidates().size());
    int derived = 0;
    int excluded = 0;
    long days = ctx.campaignDays();

    for (Inventory inv : ctx.getCandidates()) {
      String key = inv.getReferenceId() != null ? inv.getReferenceId() : inv.getInventoryId();
      V3RunContext.MeasureData measure = measureMap.get(key);
      if (measure != null && measure.valid()) {
        retained.add(inv);
        continue;
      }

      AudienceData audience = ctx.audienceFor(inv);
      Long impressions = deriveImpressions(audience, ctx.getRequest().getStartDate(), days);
      Long reach = deriveReach(audience, ctx.getRequest().getStartDate(), days);
      if (impressions != null && impressions > 0) {
        measureMap.put(
            key,
            new V3RunContext.MeasureData(
                impressions, reach != null && reach > 0 ? reach : null, "derived"));
        retained.add(inv);
        derived++;
      } else {
        excluded++;
        ctx.getWarnings().exclude("NO_IMPRESSIONS_DATA");
      }
    }

    if (derived > 0) {
      ctx.getWarnings()
          .warn(derived + " inventories scored with derived (non-Measure) impressions data");
    }
    if (excluded > 0) {
      ctx.getWarnings()
          .warn(
              excluded
                  + " inventories excluded because no impressions data (Measure or derived) was"
                  + " available");
    }
    ctx.setMeasureByReferenceId(measureMap);
    ctx.setCandidates(retained);
  }

  /** Sums daily visitors over the window, else prorates the monthly total (PRD §5.3 source). */
  static Long deriveImpressions(AudienceData audience, LocalDate startDate, long days) {
    if (audience == null) {
      return null;
    }
    if (audience.getDailySummary() != null && !audience.getDailySummary().isEmpty()) {
      long total = 0;
      boolean any = false;
      for (int i = 0; i < days; i++) {
        LocalDate date = startDate.plusDays(i);
        AudienceData.DailySummary daily = audience.getDailySummary().get(date.getDayOfMonth());
        if (daily != null && daily.getTotalVisitors() != null) {
          total += daily.getTotalVisitors();
          any = true;
        }
      }
      if (any) {
        return total;
      }
    }
    if (audience.getMonthlySummary() != null
        && audience.getMonthlySummary().getTotalVisitors() != null) {
      return Math.round(audience.getMonthlySummary().getTotalVisitors() * days / 30.0);
    }
    return null;
  }

  static Long deriveReach(AudienceData audience, LocalDate startDate, long days) {
    if (audience == null) {
      return null;
    }
    if (audience.getDailySummary() != null && !audience.getDailySummary().isEmpty()) {
      long total = 0;
      boolean any = false;
      for (int i = 0; i < days; i++) {
        LocalDate date = startDate.plusDays(i);
        AudienceData.DailySummary daily = audience.getDailySummary().get(date.getDayOfMonth());
        if (daily != null && daily.getUniqueVisitors() != null) {
          total += daily.getUniqueVisitors();
          any = true;
        }
      }
      if (any) {
        return total;
      }
    }
    if (audience.getMonthlySummary() != null
        && audience.getMonthlySummary().getUniqueVisitors() != null) {
      return Math.round(audience.getMonthlySummary().getUniqueVisitors() * days / 30.0);
    }
    return null;
  }

  private static <T> List<List<T>> partition(List<T> list, int size) {
    List<List<T>> parts = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      parts.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return parts;
  }
}
