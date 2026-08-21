package com.mw.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.SelectCampaignInventoryRequestDTO;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.InventoryRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for Campaign Inventory Controller endpoints. Tests the complete workflow from
 * filtering to selecting to updating campaign inventory schedules.
 */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@Import(TestcontainersConfiguration.class)
class CampaignInventoryControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private InventoryRepository inventoryRepository;

  @Autowired private CampaignInventorySchedulesRepository schedulesRepository;

  @Autowired private ScheduleRepository scheduleRepository;

  @Autowired private CampaignRepository campaignRepository;

  @MockitoBean private UserService userService;

  private ObjectMapper objectMapper;
  private Inventory testInventory;
  private Campaign testCampaign;

  @BeforeEach
  void setUp() {

    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    // Mock UserService to return a valid IamUserContext
    IamUserContext testIamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();
    when(userService.getIamUserContext()).thenReturn(testIamUserContext);

    // Clean up database
    schedulesRepository.deleteAll();
    scheduleRepository.deleteAll();
    inventoryRepository.deleteAll();
    campaignRepository.deleteAll();

    // Setup test inventory
    testInventory = new Inventory();
    testInventory.setId("inventory123");
    testInventory.setName("Test Inventory");
    testInventory.setArchived(false); // active = !archived
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

    // Setup test campaign
    testCampaign = new Campaign();
    testCampaign.setId("campaign123");
    testCampaign.setName("Test Campaign");
    testCampaign.setDescription("Test Campaign Description");
    testCampaign.setStatus(Campaign.Status.DRAFT);
    testCampaign.setBudget(10000.0);
    testCampaign.setCurrency("USD");
    testCampaign.setStartDate(java.time.LocalDate.now());
    testCampaign.setEndDate(java.time.LocalDate.now().plusDays(30));
    testCampaign.setUserId("user123");
    testCampaign.setClientType(Campaign.ClientType.DIRECT_ADVERTISER);
    testCampaign.setCompanyId("company123");
    campaignRepository.save(testCampaign);
  }

  @Test
  @DisplayName("Integration - Select and deselect workflow")
  void integration_SelectAndDeselectWorkflow_ShouldWorkCorrectly() throws Exception {
    String campaignId = "campaign123";
    String inventoryId = "inventory123";

    // Step 1: Select campaign inventory
    SelectCampaignInventoryRequestDTO selectRequest = new SelectCampaignInventoryRequestDTO();
    selectRequest.setInventoryId(inventoryId);
    selectRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/{campaignId}/select", campaignId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(selectRequest)))
        .andExpect(status().isOk());

    // Verify schedule was created
    var schedule =
        schedulesRepository.findByCampaignIdAndInventoryId(campaignId, inventoryId).orElse(null);
    assertThat(schedule).isNotNull();
    assertThat(schedule.getScheduleIds()).isNotEmpty();
    var savedSchedule = scheduleRepository.findById(schedule.getScheduleIds().get(0)).orElse(null);
    assertThat(savedSchedule).isNotNull();
    assertThat(savedSchedule.getBookingMatrix()).isNotEmpty();

    // Step 2: Deselect campaign inventory
    SelectCampaignInventoryRequestDTO deselectRequest = new SelectCampaignInventoryRequestDTO();
    deselectRequest.setInventoryId(inventoryId);
    deselectRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/{campaignId}/select", campaignId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deselectRequest)))
        .andExpect(status().isOk());

    // Verify schedule was deleted
    var count =
        schedulesRepository.findByCampaignIdAndInventoryId(campaignId, inventoryId).orElse(null);
    assertThat(count).isNull();
  }

  @Test
  @DisplayName("Integration - Get all selected inventories returns every record without pagination")
  void integration_GetAllSelectedInventories_ShouldReturnAllRecordsWithSlimPayload()
      throws Exception {
    String campaignId = "campaign123";
    when(userService.getPrimaryCompanyId()).thenReturn("company123");

    // Seed more selected inventories than the paginated endpoint's default page size (10)
    int totalRecords = 15;
    for (int i = 1; i <= totalRecords; i++) {
      Inventory inventory = new Inventory();
      inventory.setId("bulk-inventory-" + i);
      inventory.setInventoryId("env-inventory-" + i);
      inventory.setReferenceId("ref-" + i);
      inventory.setName("Bulk Inventory " + i);
      inventory.setArchived(false);
      inventory.setType("DIGITAL");
      inventory.setMediaOwnerId("mediaOwner123");
      inventoryRepository.save(inventory);

      CampaignInventorySchedules schedule =
          CampaignInventorySchedules.builder()
              .campaignId(campaignId)
              .mediaOwnerId("mediaOwner123")
              .inventoryId("bulk-inventory-" + i)
              .build();
      schedulesRepository.save(schedule);
    }

    var result =
        mockMvc
            .perform(
                get("/api/v1/campaign-inventory/{campaignId}/selected-inventory/all", campaignId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(totalRecords))
            .andReturn();

    // Each element carries only the slim fields: inventoryId, referenceId, performance
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    Set<String> allowedFields = Set.of("inventoryId", "referenceId", "performance");
    for (JsonNode node : data) {
      node.fieldNames().forEachRemaining(field -> assertThat(field).isIn(allowedFields));
      assertThat(node.get("inventoryId").asText()).startsWith("env-inventory-");
      assertThat(node.get("referenceId").asText()).startsWith("ref-");
    }
  }
}
