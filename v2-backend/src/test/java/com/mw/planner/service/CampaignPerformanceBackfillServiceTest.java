package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CampaignPerformanceBackfillServiceTest {

  @Mock private CampaignService campaignService;
  @Mock private CampaignInventorySchedulesService campaignInventorySchedulesService;
  @Mock private CampaignRepository campaignRepository;
  @Mock private VirtualThreadService virtualThreadService;
  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private ObjectMapper objectMapper;
  private MwPlannerProperties properties;
  private CampaignPerformanceBackfillService service;

  private static final String TOKEN = "measure-bearer-token";
  private static final String USERNAME = "admin";

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
    properties = new MwPlannerProperties();
    service =
        new CampaignPerformanceBackfillService(
            campaignService,
            campaignInventorySchedulesService,
            campaignRepository,
            virtualThreadService,
            redisTemplate,
            objectMapper,
            properties);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    // Run the sweep inline so assertions can observe its effects synchronously.
    lenient()
        .when(virtualThreadService.runAsync(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return CompletableFuture.completedFuture(null);
            });
  }

  private void lockAcquired() {
    when(valueOperations.setIfAbsent(
            eq(CampaignPerformanceBackfillService.LOCK_KEY), anyString(), any(Duration.class)))
        .thenReturn(true);
  }

  private Campaign campaign(String id, Campaign.Status status) {
    Campaign c =
        Campaign.builder()
            .name("campaign-" + id)
            .status(status)
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 1, 31))
            .userId("user-1")
            .clientType(Campaign.ClientType.AGENCY)
            .companyId("company-1")
            .build();
    c.setId(id);
    return c;
  }

  private CampaignForecastDTO validForecast() {
    return CampaignForecastDTO.builder()
        .totalInventories(2)
        .estimatedImpression(100L)
        .estimatedReach(50L)
        .estimatedFrequency(2.0)
        .estimatedAdPlays(10L)
        .sov(10.0)
        .avgCpm(1.0)
        .avgECpm(1.0)
        .totalCost(500.0)
        .plannedSot(100.0)
        .totalSot(200.0)
        .build();
  }

  private CampaignForecastDTO allZeroForecast() {
    return CampaignForecastDTO.builder()
        .totalInventories(0)
        .estimatedImpression(0L)
        .estimatedReach(0L)
        .estimatedFrequency(0.0)
        .estimatedAdPlays(0L)
        .sov(0.0)
        .avgCpm(0.0)
        .avgECpm(0.0)
        .totalCost(0.0)
        .plannedSot(0.0)
        .totalSot(0.0)
        .build();
  }

  private void singleBatch(Campaign... campaigns) {
    when(campaignRepository.findByPerformanceNullAndStatusIn(anyList(), any(), eq(50)))
        .thenReturn(List.of(campaigns))
        .thenReturn(List.of());
  }

  private PerformanceBackfillJobStatusDTO lastPersistedJobStatus() throws Exception {
    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations, org.mockito.Mockito.atLeastOnce())
        .set(
            org.mockito.ArgumentMatchers.startsWith(
                CampaignPerformanceBackfillService.JOB_KEY_PREFIX),
            jsonCaptor.capture(),
            any(Duration.class));
    return objectMapper.readValue(jsonCaptor.getValue(), PerformanceBackfillJobStatusDTO.class);
  }

  @Test
  @DisplayName("a valid forecast is persisted via the conditional single-field update only")
  void backfill_persistsValidForecast() throws Exception {
    lockAcquired();
    Campaign c = campaign("c1", Campaign.Status.PLANNED);
    singleBatch(c);
    List<CampaignInventorySchedules> schedules =
        List.of(
            CampaignInventorySchedules.builder()
                .campaignId("c1")
                .mediaOwnerId("mo")
                .inventoryId("inv")
                .build());
    when(campaignInventorySchedulesService.findByCampaignId("c1")).thenReturn(schedules);
    CampaignForecastDTO forecast = validForecast();
    when(campaignService.calculateCampaignForecast(c, schedules)).thenReturn(forecast);
    when(campaignRepository.setPerformanceIfNull("c1", forecast)).thenReturn(true);

    service.startBackfill(null, TOKEN, USERNAME);

    verify(campaignRepository).setPerformanceIfNull("c1", forecast);
    verify(campaignRepository, never()).save(any());
    PerformanceBackfillJobStatusDTO status = lastPersistedJobStatus();
    assertThat(status.getState()).isEqualTo(PerformanceBackfillJobStatusDTO.State.COMPLETED);
    assertThat(status.getPersisted()).isEqualTo(1);
    assertThat(status.getProcessed()).isEqualTo(1);
    assertThat(status.getFailed()).isZero();
  }

  @Test
  @DisplayName("already-populated campaign (conditional update misses) is counted as skipped")
  void backfill_doesNotOverwriteExistingPerformance() throws Exception {
    lockAcquired();
    Campaign c = campaign("c1", Campaign.Status.PLANNED);
    singleBatch(c);
    when(campaignInventorySchedulesService.findByCampaignId("c1")).thenReturn(List.of());
    when(campaignService.calculateCampaignForecast(eq(c), anyList())).thenReturn(validForecast());
    when(campaignRepository.setPerformanceIfNull(eq("c1"), any())).thenReturn(false);

    service.startBackfill(null, TOKEN, USERNAME);

    PerformanceBackfillJobStatusDTO status = lastPersistedJobStatus();
    assertThat(status.getSkippedAlreadyPopulated()).isEqualTo(1);
    assertThat(status.getPersisted()).isZero();
  }

  @Test
  @DisplayName("null, incomplete, NaN and all-zero forecasts persist nothing")
  void backfill_skipsInvalidForecasts() throws Exception {
    lockAcquired();
    CampaignForecastDTO incomplete = validForecast();
    incomplete.setSov(null);
    CampaignForecastDTO nan = validForecast();
    nan.setTotalCost(Double.NaN);
    List<CampaignForecastDTO> invalidForecasts = new ArrayList<>();
    invalidForecasts.add(null);
    invalidForecasts.add(incomplete);
    invalidForecasts.add(nan);
    invalidForecasts.add(allZeroForecast());

    Campaign c1 = campaign("c1", Campaign.Status.PLANNED);
    Campaign c2 = campaign("c2", Campaign.Status.PLANNED);
    Campaign c3 = campaign("c3", Campaign.Status.PLANNED);
    Campaign c4 = campaign("c4", Campaign.Status.PLANNED);
    singleBatch(c1, c2, c3, c4);
    when(campaignInventorySchedulesService.findByCampaignId(anyString())).thenReturn(List.of());
    when(campaignService.calculateCampaignForecast(any(Campaign.class), anyList()))
        .thenReturn(
            invalidForecasts.get(0),
            invalidForecasts.get(1),
            invalidForecasts.get(2),
            invalidForecasts.get(3));

    service.startBackfill(null, TOKEN, USERNAME);

    verify(campaignRepository, never()).setPerformanceIfNull(anyString(), any());
    PerformanceBackfillJobStatusDTO status = lastPersistedJobStatus();
    assertThat(status.getSkippedInvalid()).isEqualTo(4);
    assertThat(status.getPersisted()).isZero();
  }

  @Test
  @DisplayName("second concurrent start is rejected by the Redis lock and does not sweep")
  void backfill_rejectsConcurrentRun() {
    when(valueOperations.setIfAbsent(
            eq(CampaignPerformanceBackfillService.LOCK_KEY), anyString(), any(Duration.class)))
        .thenReturn(false);

    assertThatThrownBy(() -> service.startBackfill(null, TOKEN, USERNAME))
        .isInstanceOf(PerformanceBackfillAlreadyRunningException.class);

    verify(virtualThreadService, never()).runAsync(any(Runnable.class));
    verify(campaignRepository, never())
        .findByPerformanceNullAndStatusIn(anyList(), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  @DisplayName("default status filter is every status except DRAFT; explicit filter passes through")
  void backfill_defaultStatusesExcludeDraft() {
    lockAcquired();
    when(campaignRepository.findByPerformanceNullAndStatusIn(anyList(), any(), eq(50)))
        .thenReturn(List.of());

    service.startBackfill(null, TOKEN, USERNAME);

    ArgumentCaptor<List<Campaign.Status>> statusCaptor = ArgumentCaptor.forClass(List.class);
    verify(campaignRepository)
        .findByPerformanceNullAndStatusIn(statusCaptor.capture(), any(), eq(50));
    assertThat(statusCaptor.getValue())
        .doesNotContain(Campaign.Status.DRAFT)
        .hasSize(Campaign.Status.values().length - 1);
  }

  @Test
  @DisplayName("explicit status filter (including DRAFT) is used as-is")
  void backfill_explicitStatusesPassThrough() {
    lockAcquired();
    when(campaignRepository.findByPerformanceNullAndStatusIn(anyList(), any(), eq(50)))
        .thenReturn(List.of());

    service.startBackfill(List.of(Campaign.Status.DRAFT), TOKEN, USERNAME);

    verify(campaignRepository)
        .findByPerformanceNullAndStatusIn(eq(List.of(Campaign.Status.DRAFT)), any(), eq(50));
  }

  @Test
  @DisplayName("a per-campaign failure is counted and does not abort the sweep")
  void backfill_perCampaignFailureIsIsolated() throws Exception {
    lockAcquired();
    Campaign c1 = campaign("c1", Campaign.Status.PLANNED);
    Campaign c2 = campaign("c2", Campaign.Status.PLANNED);
    singleBatch(c1, c2);
    when(campaignInventorySchedulesService.findByCampaignId(anyString())).thenReturn(List.of());
    CampaignForecastDTO forecast = validForecast();
    when(campaignService.calculateCampaignForecast(any(Campaign.class), anyList()))
        .thenThrow(new RuntimeException("measure api down"))
        .thenReturn(forecast);
    when(campaignRepository.setPerformanceIfNull("c2", forecast)).thenReturn(true);

    service.startBackfill(null, TOKEN, USERNAME);

    verify(campaignRepository).setPerformanceIfNull("c2", forecast);
    PerformanceBackfillJobStatusDTO status = lastPersistedJobStatus();
    assertThat(status.getFailed()).isEqualTo(1);
    assertThat(status.getPersisted()).isEqualTo(1);
    assertThat(status.getState()).isEqualTo(PerformanceBackfillJobStatusDTO.State.COMPLETED);
    assertThat(status.getLastError()).contains("measure api down");
  }

  @Test
  @DisplayName(
      "worker threads see a SecurityContext with the raw token as String credentials, cleared"
          + " afterwards")
  void backfill_seedsAndClearsSecurityContext() {
    lockAcquired();
    Campaign c = campaign("c1", Campaign.Status.PLANNED);
    singleBatch(c);
    when(campaignInventorySchedulesService.findByCampaignId("c1")).thenReturn(List.of());
    AtomicReference<Object> credentialsSeen = new AtomicReference<>();
    AtomicReference<String> nameSeen = new AtomicReference<>();
    when(campaignService.calculateCampaignForecast(eq(c), anyList()))
        .thenAnswer(
            invocation -> {
              Authentication auth = SecurityContextHolder.getContext().getAuthentication();
              credentialsSeen.set(auth == null ? null : auth.getCredentials());
              nameSeen.set(auth == null ? null : auth.getName());
              return validForecast();
            });
    when(campaignRepository.setPerformanceIfNull(eq("c1"), any())).thenReturn(true);

    service.startBackfill(null, TOKEN, USERNAME);

    assertThat(credentialsSeen.get()).isEqualTo(TOKEN);
    assertThat(nameSeen.get()).isEqualTo(USERNAME);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("the lock is released after the sweep completes")
  void backfill_releasesLockAfterSweep() {
    AtomicReference<String> lockValue = new AtomicReference<>();
    when(valueOperations.setIfAbsent(
            eq(CampaignPerformanceBackfillService.LOCK_KEY), anyString(), any(Duration.class)))
        .thenAnswer(
            invocation -> {
              lockValue.set(invocation.getArgument(1));
              return true;
            });
    when(valueOperations.get(CampaignPerformanceBackfillService.LOCK_KEY))
        .thenAnswer(invocation -> lockValue.get());
    when(campaignRepository.findByPerformanceNullAndStatusIn(anyList(), any(), eq(50)))
        .thenReturn(List.of());

    service.startBackfill(null, TOKEN, USERNAME);

    // sweep ran inline in startBackfill via the mocked executor
    verify(redisTemplate).delete(CampaignPerformanceBackfillService.LOCK_KEY);
  }

  @Test
  @DisplayName("getJobStatus returns the stored status and throws for an unknown job id")
  void getJobStatus_returnsStoredStatusOrThrows() throws Exception {
    PerformanceBackfillJobStatusDTO stored =
        PerformanceBackfillJobStatusDTO.builder()
            .jobId("job-1")
            .state(PerformanceBackfillJobStatusDTO.State.RUNNING)
            .processed(3)
            .build();
    when(valueOperations.get(CampaignPerformanceBackfillService.JOB_KEY_PREFIX + "job-1"))
        .thenReturn(objectMapper.writeValueAsString(stored));
    when(valueOperations.get(CampaignPerformanceBackfillService.JOB_KEY_PREFIX + "missing"))
        .thenReturn(null);

    PerformanceBackfillJobStatusDTO result = service.getJobStatus("job-1");
    assertThat(result.getJobId()).isEqualTo("job-1");
    assertThat(result.getProcessed()).isEqualTo(3);

    assertThatThrownBy(() -> service.getJobStatus("missing"))
        .isInstanceOf(PerformanceBackfillJobNotFoundException.class);
  }
}
