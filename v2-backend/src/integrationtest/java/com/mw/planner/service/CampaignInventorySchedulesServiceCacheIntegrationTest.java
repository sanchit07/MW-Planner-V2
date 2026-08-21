package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.SelectCampaignInventoryRequestDTO;
import com.mw.planner.exception.campaign.CampaignInventorySchedulesNotFoundException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.InventoryRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CampaignInventorySchedulesServiceCacheIntegrationTest {

  @Autowired private CampaignInventorySchedulesService campaignInventorySchedulesService;

  @Autowired private CampaignInventorySchedulesRepository scheduleRepository;

  @Autowired private InventoryRepository inventoryRepository;

  @Autowired private CampaignRepository campaignRepository;

  @Autowired private CacheManager cacheManager;

  private CampaignInventorySchedules testSchedule;
  private SelectCampaignInventoryRequestDTO testSelectRequest;
  private Inventory testInventory;
  private Campaign testCampaign;

  @BeforeEach
  void setUp() {
    // Clear cache before each test
    cacheManager
        .getCacheNames()
        .forEach(
            cacheName -> {
              var cache = cacheManager.getCache(cacheName);
              if (cache != null) {
                cache.clear();
              }
            });

    // Clean up database before each test to avoid duplicate records
    scheduleRepository.deleteAll();
    inventoryRepository.deleteAll();
    campaignRepository.deleteAll();

    // Setup test campaign
    testCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Campaign Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .countryId("US")
            .build();
    testCampaign.setId("campaign123");
    campaignRepository.save(testCampaign);

    // Setup test inventory
    testInventory = new Inventory();
    testInventory.setId("inventory123");
    testInventory.setName("Test Inventory");
    testInventory.setArchived(false);
    testInventory.setExternalId("ext123");
    testInventory.setReferenceId("ref123");
    testInventory.setType("DIGITAL");
    testInventory.setEnvironment("OUTDOOR");
    testInventory.setFormat("LED");
    testInventory.setMediaOwnerId("mediaOwner123");

    // Setup operating times
    Inventory.OperatingTime operatingTime = new Inventory.OperatingTime();
    operatingTime.setStart("00:00:00");
    operatingTime.setEnd("23:59:00");
    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimesMap = new HashMap<>();
    operatingTimesMap.put(Inventory.Weekday.MONDAY, List.of(operatingTime));
    operatingTimesMap.put(Inventory.Weekday.TUESDAY, List.of(operatingTime));
    testInventory.setOperatingTimes(operatingTimesMap);

    // Setup digital fields
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setSpotDuration(30);
    testInventory.setDigitalFields(digitalFields);
    inventoryRepository.save(testInventory);

    // Setup test schedule
    testSchedule = new CampaignInventorySchedules();
    testSchedule.setId("schedule123");
    testSchedule.setCampaignId("campaign123");
    testSchedule.setMediaOwnerId("mediaOwner123");
    testSchedule.setInventoryId("inventory123");

    // Setup test select request
    testSelectRequest = new SelectCampaignInventoryRequestDTO();
    testSelectRequest.setCampaignId("campaign123");
    testSelectRequest.setInventoryId("inventory123");
    testSelectRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);
  }

  @Test
  @DisplayName("Cache Integration - Should cache config when found")
  void cacheIntegration_WhenConfigExists_ShouldCacheResult() {
    // Given
    scheduleRepository.save(testSchedule);

    // When - First call should hit database
    CampaignInventorySchedules result1 =
        campaignInventorySchedulesService.findByCampaignIdAndInventoryId(
            "campaign123", "inventory123");

    // When - Second call should hit cache
    CampaignInventorySchedules result2 =
        campaignInventorySchedulesService.findByCampaignIdAndInventoryId(
            "campaign123", "inventory123");

    // Then
    assertThat(result1).isNotNull();
    assertThat(result2).isNotNull();
    assertThat(result1.getId()).isEqualTo(result2.getId());
    assertThat(result1.getCampaignId()).isEqualTo(result2.getCampaignId());
    assertThat(result1.getInventoryId()).isEqualTo(result2.getInventoryId());

    // Verify cache contains the entry
    String cacheKey = "campaign123_inventory123";
    var cache = cacheManager.getCache("campaignInventorySchedules");
    assertThat(cache).isNotNull();
    assertThat(cache.get(cacheKey, CampaignInventorySchedules.class)).isNotNull();
  }

  @Test
  @DisplayName("Cache Integration - Should throw exception when config not found")
  void cacheIntegration_WhenConfigNotFound_ShouldThrowException() {
    // Given - No config in database

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.findByCampaignIdAndInventoryId(
                    "nonexistent", "nonexistent"))
        .isInstanceOf(CampaignInventorySchedulesNotFoundException.class)
        .hasMessageContaining(
            "Campaign inventory schedules not found for campaignId: nonexistent, inventoryId: nonexistent")
        .hasMessageContaining("campaignId: nonexistent")
        .hasMessageContaining("inventoryId: nonexistent");
  }

  @Test
  @DisplayName("Cache Integration - Should evict cache when config is deleted")
  void cacheIntegration_WhenConfigDeleted_ShouldEvictCache() {
    // Given
    scheduleRepository.save(testSchedule);

    // When - First call to populate cache
    campaignInventorySchedulesService.findByCampaignIdAndInventoryId("campaign123", "inventory123");

    // Verify cache is populated
    String cacheKey = "campaign123_inventory123";
    var cache = cacheManager.getCache("campaignInventorySchedules");
    assertThat(cache).isNotNull();
    assertThat(cache.get(cacheKey, CampaignInventorySchedules.class)).isNotNull();

    // When - Delete config (this should evict cache)
    SelectCampaignInventoryRequestDTO deselectRequest = new SelectCampaignInventoryRequestDTO();
    deselectRequest.setCampaignId("campaign123");
    deselectRequest.setInventoryId("inventory123");
    deselectRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    campaignInventorySchedulesService.deselectInventory(deselectRequest);

    // Then - Cache should be evicted
    var evictedCache = cacheManager.getCache("campaignInventorySchedules");
    assertThat(evictedCache).isNotNull();
    assertThat(evictedCache.get(cacheKey, CampaignInventorySchedules.class)).isNull();
  }

  @Test
  @DisplayName("Cache Integration - Should handle cache miss gracefully")
  void cacheIntegration_WhenCacheMiss_ShouldHandleGracefully() {
    // Given - No config in database and cache is empty

    // When & Then
    assertThatThrownBy(
            () ->
                campaignInventorySchedulesService.findByCampaignIdAndInventoryId(
                    "nonexistent", "nonexistent"))
        .isInstanceOf(CampaignInventorySchedulesNotFoundException.class);

    // Verify cache remains empty (no entry should be cached for non-existent schedule)
    String cacheKey = "nonexistent_nonexistent";
    var emptyCache = cacheManager.getCache("campaignInventorySchedules");
    assertThat(emptyCache).isNotNull();
    assertThat(emptyCache.get(cacheKey, CampaignInventorySchedules.class)).isNull();
  }
}
