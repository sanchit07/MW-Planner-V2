package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.dto.CampaignForecastDTO;
import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CampaignPerformanceBackfillRepositoryIntegrationTest {

  private static final String NAME_PREFIX = "perf-backfill-it-";

  @Autowired private CampaignRepository campaignRepository;
  @Autowired private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Autowired private MongoTemplate mongoTemplate;

  @BeforeEach
  void cleanUp() {
    mongoTemplate.remove(
        new Query(Criteria.where("name").regex("^" + NAME_PREFIX)), Campaign.class);
    mongoTemplate.remove(
        new Query(Criteria.where("mediaOwnerId").is(NAME_PREFIX + "mo")),
        CampaignInventorySchedules.class);
  }

  private Campaign seedCampaign(String suffix, Campaign.Status status, CampaignForecastDTO perf) {
    Campaign campaign =
        Campaign.builder()
            .name(NAME_PREFIX + suffix)
            .status(status)
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 1, 31))
            .userId("user-1")
            .clientType(Campaign.ClientType.AGENCY)
            .companyId("company-1")
            .performance(perf)
            .build();
    return campaignRepository.save(campaign);
  }

  private CampaignForecastDTO validForecast() {
    return CampaignForecastDTO.builder()
        .totalInventories(3)
        .estimatedImpression(1_000_000L)
        .estimatedReach(50_000L)
        .estimatedFrequency(2.5)
        .estimatedAdPlays(5000L)
        .sov(15.5)
        .avgCpm(4.2)
        .avgECpm(3.8)
        .totalCost(10_000.50)
        .plannedSot(5000.0)
        .totalSot(10_000.0)
        .build();
  }

  private Document rawCampaign(String id) {
    return mongoTemplate.findOne(
        new Query(Criteria.where("_id").is(id)), Document.class, "campaigns");
  }

  @Test
  @DisplayName(
      "setPerformanceIfNull persists the forecast as a single-field update without touching audit"
          + " fields or any other field")
  void setPerformanceIfNull_persistsOnlyPerformance() {
    Campaign campaign = seedCampaign("a", Campaign.Status.PLANNED, null);
    CampaignInventorySchedules schedule =
        campaignInventorySchedulesRepository.save(
            CampaignInventorySchedules.builder()
                .campaignId(campaign.getId())
                .mediaOwnerId(NAME_PREFIX + "mo")
                .inventoryId("inv-1")
                .approvedScheduleIds(List.of("sch-1"))
                .approvedBy("approver-1")
                .build());
    Document before = rawCampaign(campaign.getId());

    boolean updated = campaignRepository.setPerformanceIfNull(campaign.getId(), validForecast());

    assertThat(updated).isTrue();
    Document after = rawCampaign(campaign.getId());
    Document persistedPerformance = after.get("performance", Document.class);
    assertThat(persistedPerformance).isNotNull();
    assertThat(persistedPerformance.getDouble("sov")).isEqualTo(15.5);
    assertThat(persistedPerformance.getDouble("plannedSot")).isEqualTo(5000.0);
    assertThat(persistedPerformance.getDouble("totalSot")).isEqualTo(10_000.0);
    assertThat(persistedPerformance.getLong("estimatedImpression")).isEqualTo(1_000_000L);

    // Every other field — including createdAt/updatedAt/lastModifiedBy — must be byte-identical.
    after.remove("performance");
    before.remove("performance");
    assertThat(after).isEqualTo(before);

    // No approval-reset side effect on schedules.
    CampaignInventorySchedules scheduleAfter =
        campaignInventorySchedulesRepository.findById(schedule.getId()).orElseThrow();
    assertThat(scheduleAfter.getApprovedScheduleIds()).containsExactly("sch-1");
    assertThat(scheduleAfter.getApprovedBy()).isEqualTo("approver-1");
  }

  @Test
  @DisplayName("setPerformanceIfNull does not overwrite an already-populated performance")
  void setPerformanceIfNull_doesNotOverwrite() {
    CampaignForecastDTO existing = validForecast();
    existing.setSov(99.9);
    Campaign campaign = seedCampaign("b", Campaign.Status.PLANNED, existing);

    boolean updated = campaignRepository.setPerformanceIfNull(campaign.getId(), validForecast());

    assertThat(updated).isFalse();
    Document after = rawCampaign(campaign.getId());
    assertThat(after.get("performance", Document.class).getDouble("sov")).isEqualTo(99.9);
  }

  @Test
  @DisplayName(
      "findByPerformanceNullAndStatusIn pages by _id keyset, filters status, and excludes"
          + " already-populated campaigns")
  void findByPerformanceNullAndStatusIn_keysetPagination() {
    Campaign c1 = seedCampaign("c1", Campaign.Status.PLANNED, null);
    Campaign c2 = seedCampaign("c2", Campaign.Status.ACTIVE, null);
    Campaign c3 = seedCampaign("c3", Campaign.Status.PLANNED, null);
    seedCampaign("c4-populated", Campaign.Status.PLANNED, validForecast());
    seedCampaign("c5-draft", Campaign.Status.DRAFT, null);

    List<Campaign.Status> statuses = List.of(Campaign.Status.PLANNED, Campaign.Status.ACTIVE);
    List<String> expectedIds =
        List.of(c1.getId(), c2.getId(), c3.getId()).stream().sorted().toList();

    List<Campaign> firstPage =
        campaignRepository.findByPerformanceNullAndStatusIn(statuses, null, 2);
    assertThat(firstPage).hasSize(2);
    assertThat(firstPage.get(0).getId()).isEqualTo(expectedIds.get(0));
    assertThat(firstPage.get(1).getId()).isEqualTo(expectedIds.get(1));

    List<Campaign> secondPage =
        campaignRepository.findByPerformanceNullAndStatusIn(statuses, firstPage.get(1).getId(), 2);
    assertThat(secondPage).hasSize(1);
    assertThat(secondPage.get(0).getId()).isEqualTo(expectedIds.get(2));

    List<Campaign> thirdPage =
        campaignRepository.findByPerformanceNullAndStatusIn(statuses, secondPage.get(0).getId(), 2);
    assertThat(thirdPage).isEmpty();
  }

  @Test
  @DisplayName("a re-run only sees campaigns whose performance is still null (resume behavior)")
  void findByPerformanceNullAndStatusIn_resumesAfterPartialCompletion() {
    Campaign c1 = seedCampaign("d1", Campaign.Status.PLANNED, null);
    Campaign c2 = seedCampaign("d2", Campaign.Status.PLANNED, null);

    assertThat(campaignRepository.setPerformanceIfNull(c1.getId(), validForecast())).isTrue();

    List<Campaign> remaining =
        campaignRepository.findByPerformanceNullAndStatusIn(
            List.of(Campaign.Status.PLANNED), null, 10);
    assertThat(remaining).extracting(Campaign::getId).contains(c2.getId());
    assertThat(remaining).extracting(Campaign::getId).doesNotContain(c1.getId());
  }
}
