package com.mw.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.dto.CampaignForecastDTO;
import com.mw.planner.dto.PerformanceBackfillJobStatusDTO;
import com.mw.planner.exception.campaign.PerformanceBackfillAlreadyRunningException;
import com.mw.planner.exception.campaign.PerformanceBackfillJobNotFoundException;
import com.mw.planner.repository.CampaignRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * One-time backfill of {@code Campaign.performance} for legacy campaigns created before autosave
 * started persisting forecast snapshots. Reuses the exact forecast-generation path the campaign
 * listing uses ({@link CampaignService#calculateCampaignForecast}) and persists via a conditional
 * single-field update that never overwrites an existing snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignPerformanceBackfillService {

  static final String LOCK_KEY = "campaign:performance-backfill:lock";
  static final String JOB_KEY_PREFIX = "campaign:performance-backfill:job:";

  private final CampaignService campaignService;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final CampaignRepository campaignRepository;
  private final VirtualThreadService virtualThreadService;
  private final RedisTemplate<String, String> redisTemplate;
  private final ObjectMapper objectMapper;
  private final MwPlannerProperties mwPlannerProperties;

  /**
   * Starts an async backfill sweep and returns immediately with the job status. Only one sweep may
   * run at a time (enforced via a Redis lock).
   *
   * @param statuses campaign statuses to include; {@code null}/empty defaults to every status
   *     except {@code DRAFT} (drafts are actively edited and carry an autosave race risk)
   * @param bearerToken user JWT used for the Measure API calls on the worker threads
   * @param username display name recorded on the worker security context
   * @return the initial job status containing the job id
   */
  public PerformanceBackfillJobStatusDTO startBackfill(
      List<Campaign.Status> statuses, String bearerToken, String username) {
    List<Campaign.Status> effectiveStatuses = resolveStatuses(statuses);
    String jobId = UUID.randomUUID().toString();

    Boolean lockAcquired =
        redisTemplate
            .opsForValue()
            .setIfAbsent(
                LOCK_KEY,
                jobId,
                Duration.ofSeconds(
                    mwPlannerProperties.getPerformanceBackfill().getLockTtlSeconds()));
    if (!Boolean.TRUE.equals(lockAcquired)) {
      String runningJobId = redisTemplate.opsForValue().get(LOCK_KEY);
      throw new PerformanceBackfillAlreadyRunningException(runningJobId);
    }

    PerformanceBackfillJobStatusDTO initialStatus =
        PerformanceBackfillJobStatusDTO.builder()
            .jobId(jobId)
            .state(PerformanceBackfillJobStatusDTO.State.RUNNING)
            .statuses(effectiveStatuses.stream().map(Enum::name).toList())
            .startedAt(Instant.now())
            .build();
    persistJobStatus(initialStatus);

    log.info(
        "Starting campaign performance backfill job {} for statuses {}", jobId, effectiveStatuses);
    virtualThreadService.runAsync(() -> runSweep(jobId, effectiveStatuses, bearerToken, username));

    return initialStatus;
  }

  /**
   * Returns the stored status for a backfill job.
   *
   * @param jobId job id returned by {@link #startBackfill}
   * @return the job status
   */
  public PerformanceBackfillJobStatusDTO getJobStatus(String jobId) {
    String json = redisTemplate.opsForValue().get(JOB_KEY_PREFIX + jobId);
    if (json == null) {
      throw new PerformanceBackfillJobNotFoundException(jobId);
    }
    try {
      return objectMapper.readValue(json, PerformanceBackfillJobStatusDTO.class);
    } catch (Exception e) {
      throw new PerformanceBackfillJobNotFoundException(jobId);
    }
  }

  private void runSweep(
      String jobId, List<Campaign.Status> statuses, String bearerToken, String username) {
    int batchSize = mwPlannerProperties.getPerformanceBackfill().getBatchSize();
    Semaphore measureRateLimiter =
        new Semaphore(mwPlannerProperties.getPerformanceBackfill().getMaxConcurrentMeasureCalls());

    AtomicLong processed = new AtomicLong();
    AtomicLong persisted = new AtomicLong();
    AtomicLong skippedInvalid = new AtomicLong();
    AtomicLong skippedAlreadyPopulated = new AtomicLong();
    AtomicLong failed = new AtomicLong();
    AtomicReference<String> lastError = new AtomicReference<>();
    Instant startedAt = Instant.now();
    PerformanceBackfillJobStatusDTO.State finalState =
        PerformanceBackfillJobStatusDTO.State.COMPLETED;

    try {
      String lastId = null;
      while (true) {
        List<Campaign> batch =
            campaignRepository.findByPerformanceNullAndStatusIn(statuses, lastId, batchSize);
        if (batch.isEmpty()) {
          break;
        }
        log.info(
            "Backfill job {}: processing batch of {} campaigns: {}",
            jobId,
            batch.size(),
            batch.stream().map(Campaign::getId).toList());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Campaign campaign : batch) {
          futures.add(
              virtualThreadService.runAsync(
                  () ->
                      processCampaign(
                          jobId,
                          campaign,
                          bearerToken,
                          username,
                          measureRateLimiter,
                          processed,
                          persisted,
                          skippedInvalid,
                          skippedAlreadyPopulated,
                          failed,
                          lastError)));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        lastId = batch.get(batch.size() - 1).getId();
        persistJobStatus(
            buildStatus(
                jobId,
                PerformanceBackfillJobStatusDTO.State.RUNNING,
                statuses,
                processed,
                persisted,
                skippedInvalid,
                skippedAlreadyPopulated,
                failed,
                lastError,
                startedAt,
                null));
      }
    } catch (Exception e) {
      log.error("Campaign performance backfill job {} aborted", jobId, e);
      finalState = PerformanceBackfillJobStatusDTO.State.FAILED;
      lastError.set(e.getMessage());
    } finally {
      persistJobStatus(
          buildStatus(
              jobId,
              finalState,
              statuses,
              processed,
              persisted,
              skippedInvalid,
              skippedAlreadyPopulated,
              failed,
              lastError,
              startedAt,
              Instant.now()));
      releaseLock(jobId);
      log.info(
          "Campaign performance backfill job {} finished: processed={}, persisted={},"
              + " skippedInvalid={}, skippedAlreadyPopulated={}, failed={}",
          jobId,
          processed.get(),
          persisted.get(),
          skippedInvalid.get(),
          skippedAlreadyPopulated.get(),
          failed.get());
    }
  }

  private void processCampaign(
      String jobId,
      Campaign campaign,
      String bearerToken,
      String username,
      Semaphore measureRateLimiter,
      AtomicLong processed,
      AtomicLong persisted,
      AtomicLong skippedInvalid,
      AtomicLong skippedAlreadyPopulated,
      AtomicLong failed,
      AtomicReference<String> lastError) {
    try {
      measureRateLimiter.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      failed.incrementAndGet();
      processed.incrementAndGet();
      return;
    }
    try {
      // MwMeasureService and SecurityContextService both resolve the bearer token from the
      // authentication credentials, so worker threads need a String-credential context.
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(
          new UsernamePasswordAuthenticationToken(username, bearerToken, List.of()));
      SecurityContextHolder.setContext(context);
      try {
        List<CampaignInventorySchedules> schedules =
            campaignInventorySchedulesService.findByCampaignId(campaign.getId());
        CampaignForecastDTO forecast =
            campaignService.calculateCampaignForecast(campaign, schedules);
        String outcome;
        if (!isValidForecast(forecast)) {
          skippedInvalid.incrementAndGet();
          outcome = "SKIPPED_INVALID";
        } else if (campaignRepository.setPerformanceIfNull(campaign.getId(), forecast)) {
          persisted.incrementAndGet();
          outcome = "PERSISTED";
        } else {
          skippedAlreadyPopulated.incrementAndGet();
          outcome = "SKIPPED_ALREADY_POPULATED";
        }
        log.info("Backfill job {}: campaign {} -> {}", jobId, campaign.getId(), outcome);
      } finally {
        SecurityContextHolder.clearContext();
      }
    } catch (Exception e) {
      log.warn("Backfill job {}: campaign {} failed: {}", jobId, campaign.getId(), e.getMessage());
      failed.incrementAndGet();
      lastError.set(e.getMessage());
    } finally {
      measureRateLimiter.release();
      processed.incrementAndGet();
    }
  }

  /**
   * Only complete, finite, non-all-zero forecasts are worth freezing into the snapshot: an
   * incomplete one would be recomputed by the listing anyway ({@code isCompleteSnapshot}), NaN
   * would poison stored data (the ObjectMapper allows NaN), and an all-zero snapshot (the {@code
   * emptyForecast()} shape) would permanently mask a later-scheduled campaign.
   */
  private boolean isValidForecast(CampaignForecastDTO forecast) {
    if (forecast == null
        || forecast.getSov() == null
        || forecast.getPlannedSot() == null
        || forecast.getTotalSot() == null) {
      return false;
    }
    Double[] doubles = {
      forecast.getEstimatedFrequency(),
      forecast.getSov(),
      forecast.getAvgCpm(),
      forecast.getAvgECpm(),
      forecast.getTotalCost(),
      forecast.getPlannedSot(),
      forecast.getTotalSot()
    };
    for (Double value : doubles) {
      if (value != null && (value.isNaN() || value.isInfinite())) {
        return false;
      }
    }
    boolean allZero =
        (forecast.getTotalInventories() == null || forecast.getTotalInventories() == 0)
            && isZeroOrNull(forecast.getEstimatedImpression())
            && isZeroOrNull(forecast.getEstimatedReach())
            && isZeroOrNull(forecast.getEstimatedAdPlays())
            && isZeroOrNull(forecast.getTotalCost())
            && isZeroOrNull(forecast.getSov())
            && isZeroOrNull(forecast.getTotalSot())
            && isZeroOrNull(forecast.getPlannedSot());
    return !allZero;
  }

  private boolean isZeroOrNull(Long value) {
    return value == null || value == 0L;
  }

  private boolean isZeroOrNull(Double value) {
    return value == null || value == 0.0;
  }

  private List<Campaign.Status> resolveStatuses(List<Campaign.Status> requested) {
    if (requested != null && !requested.isEmpty()) {
      return requested;
    }
    EnumSet<Campaign.Status> defaults = EnumSet.allOf(Campaign.Status.class);
    defaults.remove(Campaign.Status.DRAFT);
    return List.copyOf(defaults);
  }

  private PerformanceBackfillJobStatusDTO buildStatus(
      String jobId,
      PerformanceBackfillJobStatusDTO.State state,
      List<Campaign.Status> statuses,
      AtomicLong processed,
      AtomicLong persisted,
      AtomicLong skippedInvalid,
      AtomicLong skippedAlreadyPopulated,
      AtomicLong failed,
      AtomicReference<String> lastError,
      Instant startedAt,
      Instant finishedAt) {
    return PerformanceBackfillJobStatusDTO.builder()
        .jobId(jobId)
        .state(state)
        .statuses(statuses.stream().map(Enum::name).toList())
        .processed(processed.get())
        .persisted(persisted.get())
        .skippedInvalid(skippedInvalid.get())
        .skippedAlreadyPopulated(skippedAlreadyPopulated.get())
        .failed(failed.get())
        .startedAt(startedAt)
        .finishedAt(finishedAt)
        .lastError(lastError.get())
        .build();
  }

  private void persistJobStatus(PerformanceBackfillJobStatusDTO status) {
    try {
      redisTemplate
          .opsForValue()
          .set(
              JOB_KEY_PREFIX + status.getJobId(),
              objectMapper.writeValueAsString(status),
              Duration.ofSeconds(
                  mwPlannerProperties.getPerformanceBackfill().getJobStatusTtlSeconds()));
    } catch (Exception e) {
      // Status reporting must never break the sweep itself.
      log.warn("Failed to persist backfill job status for {}", status.getJobId(), e);
    }
  }

  private void releaseLock(String jobId) {
    try {
      // Value-checked delete: only release a lock this job still owns (the TTL may have expired
      // and another job acquired it). GET+DEL is not atomic, acceptable for this one-shot admin
      // job — the TTL is the backstop.
      if (jobId.equals(redisTemplate.opsForValue().get(LOCK_KEY))) {
        redisTemplate.delete(LOCK_KEY);
      }
    } catch (Exception e) {
      log.warn("Failed to release backfill lock for job {}", jobId, e);
    }
  }
}
