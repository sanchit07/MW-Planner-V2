package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mw.brand.lib.service.BrandService;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.ScheduleSummaryDTO;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Full-pipeline regression test for availability-aware recommendation exclusions.
 *
 * <p>Spins up a real local {@code mongod}, seeds canonical {@code inventory_availability} records
 * (the planner's IMS-synced store) for three candidates — fully booked, partially booked, and free
 * — then runs both async recommendation services with a REAL {@link ScoringServiceImpl} + {@link
 * PlannerAvailabilityService} and asserts:
 *
 * <ul>
 *   <li>fully booked → dropped from results, counted under {@code AVAILABILITY_UNAVAILABLE} in the
 *       run metadata's {@code exclusionReasons};
 *   <li>partially booked → still recommended, but {@code allAvailable=false} with a "Limited
 *       availability" summary;
 *   <li>free (synced record, no bookings) → recommended, fully available.
 * </ul>
 */
class AvailabilityExclusionPipelineIntegrationTest {

  private static final String RUN_ID = "run-avail-001";
  private static final String CAMPAIGN_ID = "campaign-avail-001";
  // 10-day plan window: Sep 1..Sep 10 2026
  private static final LocalDate START_DATE = LocalDate.of(2026, 9, 1);
  private static final LocalDate END_DATE = LocalDate.of(2026, 9, 10);

  private static final String INV_FULL = "inv-full";
  private static final String INV_PARTIAL = "inv-partial";
  private static final String INV_FREE = "inv-free";

  // ---- local mongod lifecycle ----
  private static Process mongod;
  private static Path dbPath;
  private static String mongoUri;

  @BeforeAll
  static void startMongo() throws Exception {
    int port = freePort();
    dbPath = Files.createTempDirectory("avail-pipeline-mongo");
    mongod =
        new ProcessBuilder(
                "mongod",
                "--port",
                String.valueOf(port),
                "--dbpath",
                dbPath.toString(),
                "--bind_ip",
                "127.0.0.1")
            .redirectErrorStream(true)
            .redirectOutput(dbPath.resolve("mongod.log").toFile())
            .start();
    mongoUri = "mongodb://127.0.0.1:" + port;

    // Wait until mongod answers pings, then seed the canonical availability store.
    Exception last = null;
    for (int i = 0; i < 60; i++) {
      try (MongoClient client = MongoClients.create(mongoUri)) {
        client.getDatabase("admin").runCommand(new Document("ping", 1));
        last = null;
        break;
      } catch (Exception e) {
        last = e;
        Thread.sleep(500);
      }
    }
    if (last != null) {
      throw new IllegalStateException("mongod did not become ready", last);
    }
    seedAvailability();
  }

  @AfterAll
  static void stopMongo() throws Exception {
    if (mongod != null) {
      mongod.destroy();
      mongod.waitFor();
    }
    if (dbPath != null) {
      try (var stream = Files.walk(dbPath)) {
        stream.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  /** Seed full / partial / free canonical availability records keyed by externalId. */
  private static void seedAvailability() {
    try (MongoClient client = MongoClients.create(mongoUri)) {
      var collection =
          client.getDatabase("mw-planner").getCollection(PlannerAvailabilityServiceIT.COLLECTION);
      // Fully booked: all 4 of 4 loop positions, whole plan window (end exclusive on the hour).
      collection.insertOne(
          availabilityRecord(
              INV_FULL,
              List.of(
                  booking(
                      "DL-FULL",
                      slot("2026-09-01T00:00:00Z", "2026-09-11T00:00:00Z", List.of(1, 2, 3, 4))))));
      // Partially booked: sold out Sep 1..Sep 7 (7 of 10 days) -> 30% availability.
      collection.insertOne(
          availabilityRecord(
              INV_PARTIAL,
              List.of(
                  booking(
                      "DL-PART",
                      slot("2026-09-01T00:00:00Z", "2026-09-08T00:00:00Z", List.of(1, 2, 3, 4))))));
      // Free: synced record exists but carries no bookings/blackouts -> fully available.
      collection.insertOne(availabilityRecord(INV_FREE, List.of()));
    }
  }

  private static Document availabilityRecord(String externalId, List<Document> bookings) {
    return new Document("externalId", externalId)
        .append("payload", new Document("bookings", bookings).append("blackouts", List.of()));
  }

  private static Document booking(String dealId, Document... slots) {
    return new Document("id", UUID.randomUUID().toString())
        .append("dealId", dealId)
        .append("status", "booked")
        .append("slots", List.of(slots));
  }

  private static Document slot(String start, String end, List<Integer> positions) {
    return new Document("id", UUID.randomUUID().toString())
        .append("timeZone", "UTC")
        .append("startTime", start)
        .append("endTime", end)
        .append("slotPositions", positions);
  }

  // ---- shared collaborators, rebuilt per test ----
  private InventoryRepository inventoryRepository;
  private AudienceRepository audienceRepository;
  private BookingRepository bookingRepository;
  private BrandService brandService;
  private RecommendationRunRepository recommendationRunRepository;
  private RecommendationResultRepository recommendationResultRepository;
  private ScheduleRecommendationService scheduleRecommendationService;
  private VirtualThreadTaskExecutor virtualThreadTaskExecutor;
  private MongoTemplate mongoTemplate;
  private ScoringServiceImpl scoringService;
  private List<Inventory> inventories;

  @BeforeEach
  void setUp() {
    inventoryRepository = mock(InventoryRepository.class);
    audienceRepository = mock(AudienceRepository.class);
    bookingRepository = mock(BookingRepository.class);
    brandService = mock(BrandService.class);
    recommendationRunRepository = mock(RecommendationRunRepository.class);
    recommendationResultRepository = mock(RecommendationResultRepository.class);
    scheduleRecommendationService = mock(ScheduleRecommendationService.class);
    virtualThreadTaskExecutor = mock(VirtualThreadTaskExecutor.class);
    mongoTemplate = mock(MongoTemplate.class);

    // Deterministic synchronous "async" execution.
    lenient()
        .doAnswer(
            invocation -> {
              Runnable r = invocation.getArgument(0);
              r.run();
              return null;
            })
        .when(virtualThreadTaskExecutor)
        .execute(any());

    RecommendationRun run = new RecommendationRun();
    run.setRunId(RUN_ID);
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    lenient().when(recommendationRunRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(run));
    lenient().when(recommendationRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(mongoTemplate.insert(anyList(), any(Class.class)))
        .thenAnswer(i -> i.getArgument(0));
    lenient().when(mongoTemplate.updateFirst(any(), any(), any(Class.class))).thenReturn(null);

    // Candidates: three identical digital sites; only the seeded availability differs.
    inventories =
        List.of(buildInventory(INV_FULL), buildInventory(INV_PARTIAL), buildInventory(INV_FREE));

    // Real scoring pipeline: engine has no booking_data of its own; the canonical planner
    // store (real local mongod) is the only source of availability.
    lenient()
        .when(bookingRepository.findByInventoryIdInAndDateRange(anyList(), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(bookingRepository.findByInventoryIdAndDateRange(anyString(), any(), any()))
        .thenReturn(List.of());
    lenient().when(inventoryRepository.findByInventoryIdIn(anyList())).thenReturn(inventories);
    PlannerAvailabilityService plannerAvailabilityService =
        new PlannerAvailabilityService(mongoUri, "mw-planner");
    scoringService =
        new ScoringServiceImpl(
            inventoryRepository, bookingRepository, brandService, plannerAvailabilityService);

    // Audience enrichment: none needed.
    lenient()
        .when(audienceRepository.findByInventoryIdInOrReferenceIdIn(anyList(), anyList()))
        .thenReturn(List.of());
    lenient().when(audienceRepository.findByReferenceIdIn(anyList())).thenReturn(List.of());
    lenient().when(audienceRepository.findByInventoryIdIn(anyList())).thenReturn(List.of());

    // Schedules for auto-selection.
    lenient()
        .when(
            scheduleRecommendationService.buildBestScheduleForBudgetCap(
                any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            scheduleRecommendationService.buildScheduleSummariesForInventories(
                anyList(), eq(START_DATE), eq(END_DATE), isNull(), any()))
        .thenAnswer(
            invocation -> {
              List<Inventory> invs = invocation.getArgument(0);
              Map<String, ScheduleSummaryDTO> result = new HashMap<>();
              for (Inventory inv : invs) {
                result.put(inv.getInventoryId(), buildSchedule());
              }
              return result;
            });
  }

  private static Inventory buildInventory(String id) {
    Inventory inv = new Inventory();
    inv.setInventoryId(id);
    inv.setReferenceId("REF-" + id);
    inv.setName("Inv-" + id);
    inv.setClassification("Digital");
    inv.setType("OOH");
    Inventory.DigitalFields df = new Inventory.DigitalFields();
    df.setSpotsPerLoop(4);
    df.setLoopDuration(60);
    inv.setDigitalFields(df);
    // Operate 24h every day so availability is derived purely from bookings.
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes = new HashMap<>();
    for (Inventory.Weekday day : Inventory.Weekday.values()) {
      Inventory.OperatingTime time = new Inventory.OperatingTime();
      time.setStart("00:00:00");
      time.setEnd("23:59:59");
      operatingTimes.put(day, List.of(time));
    }
    inv.setOperatingTimes(operatingTimes);
    return inv;
  }

  private static ScheduleSummaryDTO buildSchedule() {
    return ScheduleSummaryDTO.builder()
        .scheduleId(UUID.randomUUID().toString())
        .scheduleStartDate(START_DATE)
        .scheduleEndDate(END_DATE)
        .basePrice(300.0)
        .estimatedImpressions(10000L)
        .estimatedReach(5000L)
        .adPlays(500L)
        .currency("USD")
        .bookingMatrix(Map.of("2026-09-01", List.of(9, 10, 11)))
        .build();
  }

  private static RecommendationRequestDTO buildRequest() {
    RecommendationRequestDTO req = new RecommendationRequestDTO();
    req.setCountry("US");
    req.setStartDate(START_DATE);
    req.setEndDate(END_DATE);
    req.setBudget(new BigDecimal("2000"));
    req.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    req.setGoalValue(1000000L);
    return req;
  }

  @SuppressWarnings("unchecked")
  private Map<String, RecommendationResult> insertedResults() {
    ArgumentCaptor<List<RecommendationResult>> captor = ArgumentCaptor.forClass(List.class);
    verify(mongoTemplate, atLeastOnce()).insert(captor.capture(), any(Class.class));
    Map<String, RecommendationResult> byId = new HashMap<>();
    for (List<RecommendationResult> batch : captor.getAllValues()) {
      for (RecommendationResult r : batch) {
        byId.put(r.getInventoryId(), r);
      }
    }
    return byId;
  }

  /**
   * Extracts the last persisted run metadata's exclusionReasons, wherever the service wrote it: v1
   * mutates + saves the run entity, v2 issues a targeted {@code updateFirst($set: metadata)}.
   */
  private Map<String, Integer> savedExclusionReasons() {
    Map<String, Integer> reasons = null;

    ArgumentCaptor<RecommendationRun> saveCaptor = ArgumentCaptor.forClass(RecommendationRun.class);
    verify(recommendationRunRepository, atLeast(0)).save(saveCaptor.capture());
    for (RecommendationRun saved : saveCaptor.getAllValues()) {
      if (saved.getMetadata() != null && saved.getMetadata().getExclusionReasons() != null) {
        reasons = saved.getMetadata().getExclusionReasons();
      }
    }

    ArgumentCaptor<org.springframework.data.mongodb.core.query.UpdateDefinition> updateCaptor =
        ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.UpdateDefinition.class);
    verify(mongoTemplate, atLeast(0)).updateFirst(any(), updateCaptor.capture(), any(Class.class));
    for (org.springframework.data.mongodb.core.query.UpdateDefinition update :
        updateCaptor.getAllValues()) {
      Object set = update.getUpdateObject().get("$set");
      if (set instanceof Document setDoc
          && setDoc.get("metadata") instanceof RecommendationRun.RecommendationMetadata metadata
          && metadata.getExclusionReasons() != null) {
        reasons = metadata.getExclusionReasons();
      }
    }

    assertNotNull(reasons, "run metadata with exclusionReasons must be saved");
    return reasons;
  }

  private void assertPipelineOutcome() {
    Map<String, Integer> reasons = savedExclusionReasons();
    assertEquals(
        1,
        reasons.get("AVAILABILITY_UNAVAILABLE"),
        "the fully booked candidate must be counted as AVAILABILITY_UNAVAILABLE");

    Map<String, RecommendationResult> results = insertedResults();
    assertFalse(results.containsKey(INV_FULL), "sold-out inventory must not be recommended at all");

    RecommendationResult partial = results.get(INV_PARTIAL);
    assertNotNull(partial, "partially booked inventory must still be recommended");
    RecommendationResult.AvailabilitySummary partialAvail = partial.getAvailability();
    assertNotNull(partialAvail);
    assertEquals(Boolean.FALSE, partialAvail.getAllAvailable());
    assertEquals(10, partialAvail.getTotalDays());
    assertEquals(3, partialAvail.getAvailableDays());
    assertEquals(30.0, partialAvail.getAvailabilityPercentage(), 0.01);
    assertEquals(
        "Limited availability for your dates: 3/10 days available", partialAvail.getSummary());

    RecommendationResult free = results.get(INV_FREE);
    assertNotNull(free, "free inventory must be recommended unchanged");
    RecommendationResult.AvailabilitySummary freeAvail = free.getAvailability();
    assertNotNull(freeAvail);
    assertEquals(Boolean.TRUE, freeAvail.getAllAvailable());
    assertEquals(100.0, freeAvail.getAvailabilityPercentage(), 0.01);
    assertEquals("10/10 days available", freeAvail.getSummary());
  }

  @Test
  @DisplayName("v1 pipeline: seeded availability drives exclusion, limited-availability, and free")
  void v1PipelineRespectsSeededAvailability() {
    MeasureApiClient measureApiClient = mock(MeasureApiClient.class);
    RecommendationAsyncService service =
        new RecommendationAsyncService(
            inventoryRepository,
            audienceRepository,
            scoringService,
            recommendationRunRepository,
            recommendationResultRepository,
            scheduleRecommendationService,
            virtualThreadTaskExecutor,
            mongoTemplate,
            measureApiClient,
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC),
            new AutoSelectionReasonResolver(
                new com.mw.recommendation.engine.config.MwRecommendationEngineProperties()));

    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any()))
        .thenReturn(new ArrayList<>(inventories));

    service.processRecommendationsAsync(RUN_ID, CAMPAIGN_ID, buildRequest());

    assertPipelineOutcome();
  }

  @Test
  @DisplayName("v2 pipeline: seeded availability drives exclusion, limited-availability, and free")
  void v2PipelineRespectsSeededAvailability() {
    MeasureClientV2 measureClientV2 = mock(MeasureClientV2.class);
    lenient()
        .when(measureClientV2.getReachAndFrequencyBySites(any(), anyBoolean()))
        .thenReturn(List.of());
    RecommendationAsyncServiceV2 service =
        new RecommendationAsyncServiceV2(
            inventoryRepository,
            audienceRepository,
            scoringService,
            recommendationRunRepository,
            recommendationResultRepository,
            scheduleRecommendationService,
            virtualThreadTaskExecutor,
            mongoTemplate,
            measureClientV2,
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC),
            new AutoSelectionReasonResolver(
                new com.mw.recommendation.engine.config.MwRecommendationEngineProperties()));
    // Constructor bypasses Spring @Value binding; make chunking/concurrency explicit.
    ReflectionTestUtils.setField(service, "scoringChunkSize", 1000);
    ReflectionTestUtils.setField(service, "scoringMaxConcurrency", 4);

    when(inventoryRepository.findActiveInventoriesByCountryWithGeographyTargeting(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any(),
            any(),
            any(),
            anyBoolean()))
        .thenReturn(new ArrayList<>(inventories));

    service.processRecommendationsAsyncOptimized(RUN_ID, CAMPAIGN_ID, buildRequest());

    assertPipelineOutcome();
  }

  /** Small indirection so the seeded collection name always matches the service's contract. */
  private static final class PlannerAvailabilityServiceIT {
    static final String COLLECTION = "inventory_availability";
  }
}
