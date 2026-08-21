package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * Integration tests for CampaignInventorySchedulesRepositoryImpl (custom implementation) using
 * MongoDB Testcontainers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CampaignInventorySchedulesRepositoryIntegrationTest {

  @Autowired private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private MongoTemplate mongoTemplate;

  private static final String CAMPAIGN_ID = "campaign-repo-test";
  private static final String MEDIA_OWNER_ID = "mo-1";
  private static final String INVENTORY_ID = "inv-1";

  @BeforeEach
  void setUp() {
    campaignInventorySchedulesRepository.deleteByCampaignId(CAMPAIGN_ID);
    if (inventoryRepository.existsById(INVENTORY_ID)) {
      inventoryRepository.deleteById(INVENTORY_ID);
    }
  }

  @Test
  @DisplayName(
      "existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull - when no match returns false")
  void
      existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull_WhenNoMatch_ReturnsFalse() {
    boolean result =
        campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(CAMPAIGN_ID);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName(
      "existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull - when schedule has history size > 1 and approvedBy null returns true")
  void existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull_WhenMatch_ReturnsTrue() {
    CampaignInventorySchedules.History h1 =
        CampaignInventorySchedules.History.builder()
            .userId("u1")
            .companyId("c1")
            .date(java.time.LocalDateTime.now())
            .build();
    CampaignInventorySchedules.History h2 =
        CampaignInventorySchedules.History.builder()
            .userId("u2")
            .companyId("c2")
            .date(java.time.LocalDateTime.now())
            .build();

    CampaignInventorySchedules schedule =
        CampaignInventorySchedules.builder()
            .campaignId(CAMPAIGN_ID)
            .mediaOwnerId(MEDIA_OWNER_ID)
            .inventoryId(INVENTORY_ID)
            .scheduleIds(List.of("s1"))
            .approvedBy(null)
            .history(List.of(h1, h2))
            .build();

    campaignInventorySchedulesRepository.save(schedule);

    boolean result =
        campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(CAMPAIGN_ID);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName(
      "existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull - when approvedBy set returns false")
  void
      existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull_WhenApprovedBySet_ReturnsFalse() {
    CampaignInventorySchedules.History h1 =
        CampaignInventorySchedules.History.builder()
            .userId("u1")
            .companyId("c1")
            .date(java.time.LocalDateTime.now())
            .build();
    CampaignInventorySchedules.History h2 =
        CampaignInventorySchedules.History.builder()
            .userId("u2")
            .companyId("c2")
            .date(java.time.LocalDateTime.now())
            .build();

    CampaignInventorySchedules schedule =
        CampaignInventorySchedules.builder()
            .campaignId(CAMPAIGN_ID)
            .mediaOwnerId(MEDIA_OWNER_ID)
            .inventoryId(INVENTORY_ID)
            .scheduleIds(List.of("s1"))
            .approvedBy("user-1")
            .history(List.of(h1, h2))
            .build();

    campaignInventorySchedulesRepository.save(schedule);

    boolean result =
        campaignInventorySchedulesRepository
            .existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(CAMPAIGN_ID);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName(
      "findByCampaignIdWithUnapprovedSchedules - returns schedules where approvedScheduleIds does not contain all scheduleIds")
  void findByCampaignIdWithUnapprovedSchedules_ReturnsSchedulesWithUnapprovedIds() {
    CampaignInventorySchedules schedule =
        CampaignInventorySchedules.builder()
            .campaignId(CAMPAIGN_ID)
            .mediaOwnerId(MEDIA_OWNER_ID)
            .inventoryId(INVENTORY_ID)
            .scheduleIds(List.of("s1", "s2"))
            .approvedScheduleIds(List.of("s1")) // s2 not approved
            .build();

    campaignInventorySchedulesRepository.save(schedule);

    List<CampaignInventorySchedules> result =
        campaignInventorySchedulesRepository.findByCampaignIdWithUnapprovedSchedules(
            CAMPAIGN_ID, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getScheduleIds()).containsExactlyInAnyOrder("s1", "s2");
    assertThat(result.get(0).getApprovedScheduleIds()).containsExactly("s1");
  }

  @Test
  @DisplayName("findWithPriceFilters - with campaignId and matching inventory returns page")
  void findWithPriceFilters_WithMatchingInventory_ReturnsPage() {
    // Insert inventory so lookup in aggregation finds it. Deliberately let Mongo assign the real
    // ObjectId _id (not the human-readable INVENTORY_ID constant used elsewhere in this file) —
    // production inventories are never saved with a custom _id (only externalId/referenceId are
    // set explicitly; see ExternalInventoryMessageConverter), and the inventory lookup in
    // findWithPriceFilters relies on inventoryId being a genuine ObjectId hex string.
    Inventory inventory = new Inventory();
    inventory.setName("Test Inventory");
    inventory.setType("DIGITAL");
    inventory.setArchived(false);
    Inventory.Location loc = new Inventory.Location();
    loc.setLocationCoordinates(new GeoJsonPoint(106.8, -6.2));
    loc.setCity("Jakarta");
    inventory.setLocation(loc);
    Inventory savedInventory = inventoryRepository.save(inventory);

    CampaignInventorySchedules schedule =
        CampaignInventorySchedules.builder()
            .campaignId(CAMPAIGN_ID)
            .mediaOwnerId(MEDIA_OWNER_ID)
            .inventoryId(savedInventory.getId())
            .scheduleIds(new ArrayList<>(List.of("s1")))
            .build();

    campaignInventorySchedulesRepository.save(schedule);

    Page<CampaignInventorySchedules> result =
        campaignInventorySchedulesRepository.findWithPriceFilters(
            CAMPAIGN_ID, null, PageRequest.of(0, 10), null);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getCampaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(result.getTotalElements()).isEqualTo(1);
  }
}
