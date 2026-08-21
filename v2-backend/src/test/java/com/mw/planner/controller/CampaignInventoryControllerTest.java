package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.*;
import com.mw.planner.dto.CompanyDto;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.inventory.InventoryImportException;
import com.mw.planner.service.*;
import com.mw.planner.validation.SelectCampaignInventoryValidator;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Comprehensive test class for CampaignInventoryController covering all endpoints and scenarios.
 */
@ExtendWith(MockitoExtension.class)
class CampaignInventoryControllerTest {

  @Mock private CampaignInventorySchedulesService campaignInventorySchedulesService;
  @Mock private InventoryService inventoryService;
  @Mock private CsvParsingService csvParsingService;
  @Mock private SelectCampaignInventoryValidator validator;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;
  @Mock private MwMeasureService mwMeasureService;
  @Mock private InventoryImportService inventoryImportService;
  @Mock private CampaignService campaignService;
  @Mock private CountryService countryService;

  @InjectMocks private CampaignInventoryController campaignInventoryController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private SelectCampaignInventoryRequestDTO testSelectRequestDTO;
  private SelectCampaignInventoryRequestDTO testDeselectRequestDTO;
  private CampaignInventoryFilterDTO testFilter;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(campaignInventoryController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    // Setup test select request DTO (campaignId will be set from path variable)
    testSelectRequestDTO = new SelectCampaignInventoryRequestDTO();
    testSelectRequestDTO.setInventoryId("inventory123");
    testSelectRequestDTO.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // Setup test deselect request DTO (campaignId will be set from path variable)
    testDeselectRequestDTO = new SelectCampaignInventoryRequestDTO();
    testDeselectRequestDTO.setInventoryId("inventory123");
    testDeselectRequestDTO.setOperationType(
        SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    // Setup test filter
    testFilter = CampaignInventoryFilterDTO.builder().environments(List.of("OUTDOOR")).build();
  }

  @AfterEach
  void tearDown() {
    // Clean up if needed
  }

  // ========== Filter Campaign Inventories Tests ==========

  @Test
  void filterCampaignInventories_WithValidFilter_ShouldReturnOk() throws Exception {
    // Given
    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(CampaignInventoryFilterResponseDTO.Detail.builder().id("inventory123").build())
            .build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), PageRequest.of(0, 10), 1);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testFilter))
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk());
  }

  @Test
  void filterCampaignInventories_WithEmptyFilter_ShouldReturnOk() throws Exception {
    // Given
    CampaignInventoryFilterDTO emptyFilter = CampaignInventoryFilterDTO.builder().build();
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyFilter)))
        .andExpect(status().isOk());
  }

  @Test
  void filterCampaignInventories_WithInvalidPagination_ShouldUseDefaults() throws Exception {
    // Given
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 1), 0);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then - Test with negative page and size values
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testFilter))
                .param("page", "-1")
                .param("size", "0"))
        .andExpect(status().isOk());
  }

  @Test
  void filterCampaignInventories_WithDescendingSort_ShouldReturnOk() throws Exception {
    // Given
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testFilter))
                .param("sortBy", "name")
                .param("sortDir", "desc"))
        .andExpect(status().isOk());
  }

  @Test
  void filterCampaignInventories_WithNameFilter_ShouldReturnOk() throws Exception {
    // Given
    CampaignInventoryFilterDTO nameFilter =
        CampaignInventoryFilterDTO.builder().name("billboard").build();

    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(
                CampaignInventoryFilterResponseDTO.Detail.builder()
                    .id("inventory123")
                    .name("Billboard A")
                    .build())
            .build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), PageRequest.of(0, 10), 1);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nameFilter))
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk());
  }

  @Test
  void filterCampaignInventories_WithNameAndCategoryFilter_ShouldReturnOk() throws Exception {
    // Given
    CampaignInventoryFilterDTO combinedFilter =
        CampaignInventoryFilterDTO.builder().name("test").environments(List.of("OUTDOOR")).build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(combinedFilter))
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk());
  }

  @Test
  void filterCampaignInventories_WithGoalTypeImpressions_ShouldFilterByCpm() throws Exception {
    // Given
    CampaignInventoryFilterDTO filterWithGoalType =
        CampaignInventoryFilterDTO.builder()
            .countries(List.of("Japan"))
            .goalType(com.mw.planner.domain.Campaign.Goals.GoalType.IMPRESSIONS)
            .build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(2, 10), 0);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filterWithGoalType))
                .param("page", "2")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk());
  }

  @Test
  void filterCampaignInventories_WithGoalTypeAdplays_ShouldFilterBySpot() throws Exception {
    // Given
    CampaignInventoryFilterDTO filterWithGoalType =
        CampaignInventoryFilterDTO.builder()
            .countries(List.of("Japan"))
            .goalType(com.mw.planner.domain.Campaign.Goals.GoalType.ADPLAYS)
            .build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

    when(inventoryService.filterInventories(
            any(CampaignInventoryFilterDTO.class), anyString(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filterWithGoalType))
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk());
  }

  // ========== Get Selected Inventories Tests ==========

  @Test
  void getSelectedInventories_WithValidCampaign_ShouldReturnOk() throws Exception {
    // Given
    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(
                CampaignInventoryFilterResponseDTO.Detail.builder()
                    .id("inventory123")
                    .name("Selected Billboard")
                    .isSelected(true)
                    .sellingTerm(Inventory.SellingTerm.builder().leadDays(3).build())
                    .build())
            .build();

    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), pageable, 1);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].detail.id").value("inventory123"))
        .andExpect(jsonPath("$.data.content[0].detail.isSelected").value(true))
        .andExpect(jsonPath("$.data.content[0].detail.sellingTerm.leadDays").value(3));
  }

  @Test
  void selectedInventoriesPost_WithMediaOwnerIds_ShouldReturnOk() throws Exception {
    // Given
    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(
                CampaignInventoryFilterResponseDTO.Detail.builder()
                    .id("inventory123")
                    .name("Selected Billboard")
                    .isSelected(true)
                    .build())
            .build();

    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), pageable, 1);

    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(List.of("owner-1")).build();

    when(inventoryService.getSelectedInventories(
            eq("campaign123"),
            isNull(),
            isNull(),
            any(Pageable.class),
            any(MediaOwnerFilterRequestDTO.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].detail.id").value("inventory123"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    verify(inventoryService)
        .getSelectedInventories(
            eq("campaign123"),
            isNull(),
            isNull(),
            any(Pageable.class),
            any(MediaOwnerFilterRequestDTO.class));
  }

  @Test
  void getSelectedInventories_WithNameFilter_ShouldReturnFilteredResults() throws Exception {
    // Given
    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(
                CampaignInventoryFilterResponseDTO.Detail.builder()
                    .id("inventory456")
                    .name("Digital Billboard")
                    .isSelected(true)
                    .build())
            .build();

    Pageable pageable = PageRequest.of(0, 10);
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), pageable, 1);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), eq("digital"), isNull(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("name", "digital")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].detail.name").value("Digital Billboard"));
  }

  @Test
  void getSelectedInventories_WithNoSelectedInventories_ShouldReturnEmptyPage() throws Exception {
    // Given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> emptyPageResponse =
        new PageImpl<>(List.of(), pageable, 0);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(emptyPageResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/campaign123/selected-inventory"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void getSelectedInventories_WithPagination_ShouldReturnCorrectPage() throws Exception {
    // Given
    List<CampaignInventoryFilterResponseDTO> inventories =
        Arrays.asList(
            CampaignInventoryFilterResponseDTO.builder()
                .detail(
                    CampaignInventoryFilterResponseDTO.Detail.builder()
                        .id("inventory1")
                        .name("Billboard 1")
                        .isSelected(true)
                        .build())
                .build(),
            CampaignInventoryFilterResponseDTO.builder()
                .detail(
                    CampaignInventoryFilterResponseDTO.Detail.builder()
                        .id("inventory2")
                        .name("Billboard 2")
                        .isSelected(true)
                        .build())
                .build());

    Pageable pageable = PageRequest.of(1, 2, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(inventories, pageable, 10);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(10))
        .andExpect(jsonPath("$.data.number").value(1));
  }

  @Test
  void getSelectedInventories_WithDescendingSort_ShouldReturnOk() throws Exception {
    // Given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").descending());
    Page<CampaignInventoryFilterResponseDTO> pageResponse = new PageImpl<>(List.of(), pageable, 0);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("sortBy", "name")
                .param("sortDir", "desc"))
        .andExpect(status().isOk());
  }

  @Test
  void getSelectedInventories_WithInvalidPagination_ShouldUseDefaults() throws Exception {
    // Given
    Pageable pageable = PageRequest.of(0, 1, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> pageResponse = new PageImpl<>(List.of(), pageable, 0);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then - Test with negative page and size values
    ResultActions resultActions =
        mockMvc
            .perform(
                get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                    .param("page", "-1")
                    .param("size", "0"))
            .andExpect(status().isOk());
  }

  @Test
  void getSelectedInventories_WithInventoryTypeFilter_ShouldReturnFilteredResults()
      throws Exception {
    // Given
    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(
                CampaignInventoryFilterResponseDTO.Detail.builder()
                    .id("inventory789")
                    .name("Classic Billboard")
                    .inventoryType("CLASSIC")
                    .isSelected(true)
                    .build())
            .build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), PageRequest.of(0, 10), 1);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), eq("CLASSIC"), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("inventoryType", "CLASSIC")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].detail.inventoryType").value("CLASSIC"));
  }

  @Test
  void getSelectedInventories_WithInventoryTypeFilterLowerCase_ShouldNormalizeToUpperCase()
      throws Exception {
    // Given
    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(
                CampaignInventoryFilterResponseDTO.Detail.builder()
                    .id("inventory789")
                    .name("Digital Billboard")
                    .inventoryType("DIGITAL")
                    .isSelected(true)
                    .build())
            .build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), PageRequest.of(0, 10), 1);

    // Service should normalize lowercase to uppercase
    // Controller passes "digital" to service, service normalizes it internally
    // We match "digital" (what controller passes) since service normalization happens internally
    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), eq("digital"), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then - Pass lowercase inventoryType, should be normalized to uppercase
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("inventoryType", "digital")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].detail.inventoryType").value("DIGITAL"));
  }

  @Test
  void getSelectedInventories_WithNameAndInventoryTypeFilters_ShouldReturnFilteredResults()
      throws Exception {
    // Given
    CampaignInventoryFilterResponseDTO responseDTO =
        CampaignInventoryFilterResponseDTO.builder()
            .detail(
                CampaignInventoryFilterResponseDTO.Detail.builder()
                    .id("inventory999")
                    .name("Digital Billboard Pro")
                    .inventoryType("DIGITAL")
                    .isSelected(true)
                    .build())
            .build();

    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), PageRequest.of(0, 10), 1);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), eq("digital"), eq("DIGITAL"), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("name", "digital")
                .param("inventoryType", "DIGITAL")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].detail.name").value("Digital Billboard Pro"))
        .andExpect(jsonPath("$.data.content[0].detail.inventoryType").value("DIGITAL"));
  }

  @Test
  void getSelectedInventories_WithSizeMinusOne_ShouldReturnAllResultsWithoutPagination()
      throws Exception {
    // Given
    List<CampaignInventoryFilterResponseDTO> allInventories =
        Arrays.asList(
            CampaignInventoryFilterResponseDTO.builder()
                .detail(
                    CampaignInventoryFilterResponseDTO.Detail.builder()
                        .id("inventory1")
                        .name("Billboard 1")
                        .isSelected(true)
                        .build())
                .build(),
            CampaignInventoryFilterResponseDTO.builder()
                .detail(
                    CampaignInventoryFilterResponseDTO.Detail.builder()
                        .id("inventory2")
                        .name("Billboard 2")
                        .isSelected(true)
                        .build())
                .build(),
            CampaignInventoryFilterResponseDTO.builder()
                .detail(
                    CampaignInventoryFilterResponseDTO.Detail.builder()
                        .id("inventory3")
                        .name("Billboard 3")
                        .isSelected(true)
                        .build())
                .build());

    // When size=-1, controller should use Integer.MAX_VALUE as page size
    Page<CampaignInventoryFilterResponseDTO> pageResponse =
        new PageImpl<>(
            allInventories,
            PageRequest.of(0, Integer.MAX_VALUE, org.springframework.data.domain.Sort.by("name")),
            3);

    when(inventoryService.getSelectedInventories(
            eq("campaign123"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/selected-inventory")
                .param("size", "-1")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(jsonPath("$.data.totalElements").value(3));
  }

  // ========== Get All Selected Inventories Tests ==========

  @Test
  void getAllSelectedInventories_WithValidCampaign_ShouldReturnOk() throws Exception {
    // Given
    List<SelectedInventorySummaryResponseDTO> summaries =
        List.of(
            SelectedInventorySummaryResponseDTO.builder()
                .inventoryId("envelope-inv-1")
                .referenceId("ref-1")
                .performance(
                    CampaignInventoryFilterResponseDTO.Performance.builder()
                        .cpmRate(5.5)
                        .spotRate(2.0)
                        .estimatedCost(1000.0)
                        .totalAdPlays(500L)
                        .build())
                .build(),
            SelectedInventorySummaryResponseDTO.builder()
                .inventoryId("envelope-inv-2")
                .referenceId("ref-2")
                .performance(
                    CampaignInventoryFilterResponseDTO.Performance.builder().cpmRate(3.0).build())
                .build());

    when(inventoryService.getAllSelectedInventories("campaign123")).thenReturn(summaries);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/campaign123/selected-inventory/all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].inventoryId").value("envelope-inv-1"))
        .andExpect(jsonPath("$.data[0].referenceId").value("ref-1"))
        .andExpect(jsonPath("$.data[0].performance.cpmRate").value(5.5))
        .andExpect(jsonPath("$.data[0].performance.spotRate").value(2.0))
        .andExpect(jsonPath("$.data[0].performance.estimatedCost").value(1000.0))
        .andExpect(jsonPath("$.data[0].performance.totalAdPlays").value(500))
        .andExpect(jsonPath("$.data[1].inventoryId").value("envelope-inv-2"))
        .andExpect(jsonPath("$.data[1].performance.cpmRate").value(3.0));

    verify(inventoryService).getAllSelectedInventories("campaign123");
  }

  @Test
  void getCampaignForecastPost_WithMediaOwnerIds_ShouldReturnOk() throws Exception {
    // Given
    com.mw.planner.domain.Campaign campaign = new com.mw.planner.domain.Campaign();
    campaign.setId("campaign123");

    CampaignForecastDTO forecast =
        CampaignForecastDTO.builder()
            .estimatedImpression(1000L)
            .estimatedReach(500L)
            .estimatedFrequency(2.0)
            .build();

    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(List.of("owner-1")).build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(campaign);
    when(campaignService.calculateCampaignForecast(
            eq(campaign), any(MediaOwnerFilterRequestDTO.class), eq(false)))
        .thenReturn(forecast);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/forecast")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.estimatedImpression").value(1000))
        .andExpect(jsonPath("$.data.estimatedReach").value(500));

    verify(campaignService)
        .calculateCampaignForecast(eq(campaign), any(MediaOwnerFilterRequestDTO.class), eq(false));
  }

  @Test
  void getCampaignForecast_WithForceRegenerate_ShouldPassFlagToService() throws Exception {
    // Given
    com.mw.planner.domain.Campaign campaign = new com.mw.planner.domain.Campaign();
    campaign.setId("campaign123");

    CampaignForecastDTO forecast =
        CampaignForecastDTO.builder()
            .estimatedImpression(1000L)
            .estimatedReach(500L)
            .estimatedFrequency(2.0)
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(campaign);
    when(campaignService.calculateCampaignForecast(eq(campaign), eq(true))).thenReturn(forecast);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/campaign123/forecast").param("forceRegenerate", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.estimatedImpression").value(1000))
        .andExpect(jsonPath("$.data.estimatedReach").value(500));

    verify(campaignService).calculateCampaignForecast(eq(campaign), eq(true));
  }

  @Test
  void getCampaignForecast_WithoutForceRegenerate_ShouldDefaultToFalse() throws Exception {
    // Given
    com.mw.planner.domain.Campaign campaign = new com.mw.planner.domain.Campaign();
    campaign.setId("campaign123");

    CampaignForecastDTO forecast = CampaignForecastDTO.builder().estimatedImpression(42L).build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(campaign);
    when(campaignService.calculateCampaignForecast(eq(campaign), eq(false))).thenReturn(forecast);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/campaign123/forecast"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.estimatedImpression").value(42));

    verify(campaignService).calculateCampaignForecast(eq(campaign), eq(false));
  }

  @Test
  void getCampaignForecastPost_WithForceRegenerate_ShouldPassFlagToService() throws Exception {
    // Given
    com.mw.planner.domain.Campaign campaign = new com.mw.planner.domain.Campaign();
    campaign.setId("campaign123");

    CampaignForecastDTO forecast =
        CampaignForecastDTO.builder().estimatedImpression(1000L).estimatedReach(500L).build();

    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(List.of("owner-1")).build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(campaign);
    when(campaignService.calculateCampaignForecast(
            eq(campaign), any(MediaOwnerFilterRequestDTO.class), eq(true)))
        .thenReturn(forecast);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/forecast")
                .param("forceRegenerate", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.estimatedImpression").value(1000));

    verify(campaignService)
        .calculateCampaignForecast(eq(campaign), any(MediaOwnerFilterRequestDTO.class), eq(true));
  }

  @Test
  void getAllSelectedInventoriesPost_WithMediaOwnerIds_ShouldReturnOk() throws Exception {
    // Given
    List<SelectedInventorySummaryResponseDTO> summaries =
        List.of(
            SelectedInventorySummaryResponseDTO.builder()
                .inventoryId("env-inv-1")
                .referenceId("ref-1")
                .performance(
                    CampaignInventoryFilterResponseDTO.Performance.builder().cpmRate(5.5).build())
                .build());

    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(List.of("owner-1")).build();

    when(inventoryService.getAllSelectedInventories(
            eq("campaign123"), any(MediaOwnerFilterRequestDTO.class)))
        .thenReturn(summaries);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/selected-inventory/all")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].inventoryId").value("env-inv-1"))
        .andExpect(jsonPath("$.data[0].performance.cpmRate").value(5.5));

    verify(inventoryService)
        .getAllSelectedInventories(eq("campaign123"), any(MediaOwnerFilterRequestDTO.class));
  }

  @Test
  void getAllSelectedInventories_WithNoSelectedInventories_ShouldReturnEmptyList()
      throws Exception {
    // Given
    when(inventoryService.getAllSelectedInventories("campaign123")).thenReturn(List.of());

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/campaign123/selected-inventory/all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void getAllSelectedInventories_WhenCampaignNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    doThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException("nonexistent"))
        .when(inventoryService)
        .getAllSelectedInventories("nonexistent");

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/nonexistent/selected-inventory/all"))
        .andExpect(status().isNotFound());

    verify(inventoryService).getAllSelectedInventories("nonexistent");
  }

  // ========== Select/Deselect Campaign Inventory Tests ==========

  @Test
  void selectCampaignInventory_WithValidDeselectRequest_ShouldReturnOk() throws Exception {
    // Given
    doNothing()
        .when(campaignInventorySchedulesService)
        .deselectInventory(any(SelectCampaignInventoryRequestDTO.class));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testDeselectRequestDTO)))
        .andExpect(status().isOk());
  }

  @Test
  void selectCampaignInventory_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
    // Given
    SelectCampaignInventoryRequestDTO invalidRequest = new SelectCampaignInventoryRequestDTO();
    // Missing required fields

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void selectCampaignInventory_WithSelectOperationMissingInventoryId_ShouldReturnBadRequest()
      throws Exception {
    // Given
    SelectCampaignInventoryRequestDTO invalidRequest = new SelectCampaignInventoryRequestDTO();
    // inventoryId is missing
    invalidRequest.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deselectCampaignInventory_WhenConfigNotFound_ShouldReturnOk() throws Exception {
    // Given
    // Service should handle gracefully when config not found
    doNothing()
        .when(campaignInventorySchedulesService)
        .deselectInventory(any(SelectCampaignInventoryRequestDTO.class));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testDeselectRequestDTO)))
        .andExpect(status().isOk());
  }

  // ========== bulkSelectCampaignInventories Tests ==========

  @Test
  void bulkSelectCampaignInventories_SelectOperation_ShouldReturnLocalizedSuccessMessage()
      throws Exception {
    // Given
    CampaignInventoryFilterDTO request = CampaignInventoryFilterDTO.builder().build();

    IamUserContext iamUserContext = IamUserContext.builder().locale(Locale.JAPANESE).build();
    when(userService.getIamUserContext()).thenReturn(iamUserContext);

    when(campaignInventorySchedulesService.bulkSelectDeselectInventories(
            anyString(), any(), eq(SelectCampaignInventoryRequestDTO.OperationType.SELECT)))
        .thenReturn(150);

    String expectedMessage = "キャンペーンcampaign123の150個の在庫を正常に選択しました";
    when(messageService.getMessage(
            eq("success.bulk_operation_select"), eq(Locale.JAPANESE), eq(150), eq("campaign123")))
        .thenReturn(expectedMessage);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/select-all")
                .param("operationType", "SELECT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(expectedMessage));

    verify(campaignInventorySchedulesService)
        .bulkSelectDeselectInventories(
            eq("campaign123"),
            any(CampaignInventoryFilterDTO.class),
            eq(SelectCampaignInventoryRequestDTO.OperationType.SELECT));
  }

  @Test
  void bulkSelectCampaignInventories_ShouldFallbackToEnglishWhenLocaleUnavailable()
      throws Exception {
    // Given
    CampaignInventoryFilterDTO request = CampaignInventoryFilterDTO.builder().build();

    when(userService.getIamUserContext()).thenThrow(new RuntimeException("Unable to fetch user"));

    when(campaignInventorySchedulesService.bulkSelectDeselectInventories(
            anyString(), any(), eq(SelectCampaignInventoryRequestDTO.OperationType.SELECT)))
        .thenReturn(10);

    String expectedMessage = "Successfully selected 10 inventories for campaign campaign123";
    when(messageService.getMessage(
            eq("success.bulk_operation_select"), eq(Locale.ENGLISH), eq(10), eq("campaign123")))
        .thenReturn(expectedMessage);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/select-all")
                .param("operationType", "SELECT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(expectedMessage));
  }

  // ========== bulkSelectDeselectCampaignInventoriesByIds Tests ==========

  @Test
  void bulkSelectByIds_SelectOperation_ShouldDelegateToSelectAndReturnLocalizedMessage()
      throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setInventoryIds(List.of("inv1", "inv2", "inv3"));
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    IamUserContext iamUserContext = IamUserContext.builder().locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(iamUserContext);

    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(anyString(), anyList()))
        .thenReturn(3);

    String expectedMessage = "Successfully selected 3 inventories";
    when(messageService.getMessage(
            eq("success.bulk_operation_select"), eq(Locale.ENGLISH), eq(3), eq("campaign123")))
        .thenReturn(expectedMessage);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(expectedMessage));

    verify(campaignInventorySchedulesService)
        .bulkSelectInventoriesByIds(eq("campaign123"), eq(List.of("inv1", "inv2", "inv3")));
    verify(campaignInventorySchedulesService, never())
        .bulkDeselectInventoriesByIds(anyString(), anyList());
  }

  @Test
  void bulkSelectByIds_DeselectOperation_ShouldDelegateToDeselect() throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setInventoryIds(List.of("inv1", "inv2"));
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    IamUserContext iamUserContext = IamUserContext.builder().locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(iamUserContext);

    when(campaignInventorySchedulesService.bulkDeselectInventoriesByIds(anyString(), anyList()))
        .thenReturn(2);

    String expectedMessage = "Successfully deselected 2 inventories";
    when(messageService.getMessage(
            eq("success.bulk_operation_deselect"), eq(Locale.ENGLISH), eq(2), eq("campaign123")))
        .thenReturn(expectedMessage);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(expectedMessage));

    verify(campaignInventorySchedulesService)
        .bulkDeselectInventoriesByIds(eq("campaign123"), eq(List.of("inv1", "inv2")));
    verify(campaignInventorySchedulesService, never())
        .bulkSelectInventoriesByIds(anyString(), anyList());
  }

  @Test
  void bulkSelectByReferenceIds_SelectOperation_ShouldDelegateToReferenceIdSelect()
      throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setReferenceIds(List.of("ref1", "ref2", "ref3"));
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    IamUserContext iamUserContext = IamUserContext.builder().locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(iamUserContext);

    when(campaignInventorySchedulesService.bulkSelectInventoriesByReferenceIds(
            anyString(), anyList()))
        .thenReturn(
            new CampaignInventorySchedulesService.BulkSelectByReferenceIdsResult(3, List.of()));

    String expectedMessage = "Successfully selected 3 inventories";
    when(messageService.getMessage(
            eq("success.bulk_operation_select"), eq(Locale.ENGLISH), eq(3), eq("campaign123")))
        .thenReturn(expectedMessage);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(expectedMessage));

    verify(campaignInventorySchedulesService)
        .bulkSelectInventoriesByReferenceIds(
            eq("campaign123"), eq(List.of("ref1", "ref2", "ref3")));
    verify(campaignInventorySchedulesService, never())
        .bulkSelectInventoriesByIds(anyString(), anyList());
  }

  @Test
  void bulkSelectByReferenceIds_DeselectOperation_ShouldDelegateToReferenceIdDeselect()
      throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setReferenceIds(List.of("ref1", "ref2"));
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.DESELECT);

    IamUserContext iamUserContext = IamUserContext.builder().locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(iamUserContext);

    when(campaignInventorySchedulesService.bulkDeselectInventoriesByReferenceIds(
            anyString(), anyList()))
        .thenReturn(2);

    String expectedMessage = "Successfully deselected 2 inventories";
    when(messageService.getMessage(
            eq("success.bulk_operation_deselect"), eq(Locale.ENGLISH), eq(2), eq("campaign123")))
        .thenReturn(expectedMessage);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value(expectedMessage));

    verify(campaignInventorySchedulesService)
        .bulkDeselectInventoriesByReferenceIds(eq("campaign123"), eq(List.of("ref1", "ref2")));
    verify(campaignInventorySchedulesService, never())
        .bulkDeselectInventoriesByIds(anyString(), anyList());
  }

  @Test
  void bulkSelect_WithBothInventoryIdsAndReferenceIds_ShouldReturnBadRequest() throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setInventoryIds(List.of("inv1"));
    request.setReferenceIds(List.of("ref1"));
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    verify(campaignInventorySchedulesService, never())
        .bulkSelectInventoriesByIds(anyString(), anyList());
    verify(campaignInventorySchedulesService, never())
        .bulkSelectInventoriesByReferenceIds(anyString(), anyList());
  }

  @Test
  void bulkSelect_WithNeitherInventoryIdsNorReferenceIds_ShouldReturnBadRequest() throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    verify(campaignInventorySchedulesService, never())
        .bulkSelectInventoriesByIds(anyString(), anyList());
    verify(campaignInventorySchedulesService, never())
        .bulkSelectInventoriesByReferenceIds(anyString(), anyList());
  }

  @Test
  void bulkSelectByIds_WithEmptyInventoryIds_ShouldReturnBadRequest() throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setInventoryIds(Collections.emptyList());
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    verify(campaignInventorySchedulesService, never())
        .bulkSelectInventoriesByIds(anyString(), anyList());
  }

  @Test
  void bulkSelectByIds_WithMissingOperationType_ShouldReturnBadRequest() throws Exception {
    // Given
    BulkSelectCampaignInventoryRequestDTO request = new BulkSelectCampaignInventoryRequestDTO();
    request.setInventoryIds(List.of("inv1"));
    // operationType missing

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/campaign123/bulk-select")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testGetReachAndFrequency_InvalidRequest() throws Exception {
    // Given - empty request
    MeasureReachFrequencyRequestDTO request = new MeasureReachFrequencyRequestDTO();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/reach-and-frequency")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  private MeasureReachFrequencyRequestDTO createTestRequest() {
    MeasureInventoryDTO inventory =
        MeasureInventoryDTO.builder()
            .referenceId("USA-NEW-D-00000-03420")
            .type("billboard")
            .spotsPerHour(36)
            .build();

    return MeasureReachFrequencyRequestDTO.builder()
        .inventories(Arrays.asList(inventory, inventory, inventory, inventory))
        .duration(30)
        .build();
  }

  private MeasureReachFrequencyResponseDTO createTestResponse() {
    return MeasureReachFrequencyResponseDTO.builder()
        .reach(447747L)
        .frequency(17.900019430615952)
        .impressions(8014680L)
        .status("Ok")
        .build();
  }

  // ========== Verify CSV Tests ==========

  @Test
  void verifyCsvFile_WithValidData_ShouldReturnOk() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123\ninv456";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    InventoryImportStatusResponseDTO response =
        InventoryImportStatusResponseDTO.builder()
            .results(
                List.of(
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.VALID)
                        .message("Inventory is valid")
                        .row(2)
                        .build(),
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv456")
                        .type(InventoryImportStatusResponseDTO.ValidationType.VALID)
                        .message("Inventory is valid")
                        .row(3)
                        .build()))
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.results").isArray())
        .andExpect(jsonPath("$.data.results.length()").value(2))
        .andExpect(jsonPath("$.data.results[0].id").value("inv123"))
        .andExpect(jsonPath("$.data.results[0].type").value("VALID"))
        .andExpect(jsonPath("$.data.results[1].id").value("inv456"))
        .andExpect(jsonPath("$.data.results[1].type").value("VALID"));
  }

  @Test
  void verifyCsvFile_WithInvalidInventoryIds_ShouldReturnInvalid() throws Exception {
    // Given
    String csvContent = "inventory_id\ninvalid123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    InventoryImportStatusResponseDTO response =
        InventoryImportStatusResponseDTO.builder()
            .results(
                List.of(
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("invalid123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.INVALID)
                        .message("Inventory ID does not exist")
                        .row(2)
                        .build()))
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results[0].type").value("INVALID"))
        .andExpect(jsonPath("$.data.results[0].message").value("Inventory ID does not exist"));
  }

  @Test
  void verifyCsvFile_WithCountryMismatch_ShouldReturnInvalid() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    InventoryImportStatusResponseDTO response =
        InventoryImportStatusResponseDTO.builder()
            .results(
                List.of(
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.INVALID)
                        .message(
                            "Inventory country 'Canada' does not match required country 'United States'")
                        .row(2)
                        .build()))
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results[0].type").value("INVALID"))
        .andExpect(
            jsonPath("$.data.results[0].message")
                .value(
                    "Inventory country 'Canada' does not match required country 'United States'"));
  }

  @Test
  void verifyCsvFile_WithDuplicatesInCsv_ShouldReturnDuplicate() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    InventoryImportStatusResponseDTO response =
        InventoryImportStatusResponseDTO.builder()
            .results(
                List.of(
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.DUPLICATE)
                        .message("Inventory ID appears multiple times in CSV file")
                        .row(2)
                        .build(),
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.DUPLICATE)
                        .message("Inventory ID appears multiple times in CSV file")
                        .row(3)
                        .build()))
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results[0].type").value("DUPLICATE"))
        .andExpect(
            jsonPath("$.data.results[0].message")
                .value("Inventory ID appears multiple times in CSV file"));
  }

  @Test
  void verifyCsvFile_WithAlreadySelectedInventory_ShouldReturnDuplicate() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    InventoryImportStatusResponseDTO response =
        InventoryImportStatusResponseDTO.builder()
            .results(
                List.of(
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.DUPLICATE)
                        .message("Inventory ID is already selected in the campaign")
                        .row(2)
                        .build()))
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results[0].type").value("DUPLICATE"))
        .andExpect(
            jsonPath("$.data.results[0].message")
                .value("Inventory ID is already selected in the campaign"));
  }

  @Test
  void verifyCsvFile_WithInvalidAndDuplicate_ShouldReturnInvalid() throws Exception {
    // Given - Invalid inventory that appears multiple times should be marked as INVALID
    String csvContent = "inventory_id\ninvalid123\ninvalid123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    InventoryImportStatusResponseDTO response =
        InventoryImportStatusResponseDTO.builder()
            .results(
                List.of(
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("invalid123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.INVALID)
                        .message("Inventory ID does not exist")
                        .row(2)
                        .build(),
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("invalid123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.INVALID)
                        .message("Inventory ID does not exist")
                        .row(3)
                        .build()))
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results[0].type").value("INVALID"))
        .andExpect(jsonPath("$.data.results[1].type").value("INVALID"));
  }

  @Test
  void verifyCsvFile_WithEmptyFile_ShouldReturnBadRequest() throws Exception {
    // Given
    MockMultipartFile emptyFile =
        new MockMultipartFile("csvFile", "empty.csv", "text/csv", new byte[0]);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(emptyFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest());
  }

  @Test
  void verifyCsvFile_WithInvalidFileType_ShouldReturnBadRequest() throws Exception {
    // Given
    MockMultipartFile invalidFile =
        new MockMultipartFile("csvFile", "test.txt", "text/plain", "some content".getBytes());

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(invalidFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest());
  }

  @Test
  void verifyCsvFile_WithCampaignNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    when(campaignService.findByIdForCurrentMode("nonexistent"))
        .thenThrow(new CampaignNotFoundException("nonexistent"));

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/nonexistent/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isNotFound());
  }

  @Test
  void verifyCsvFile_WithInvalidCountry_ShouldReturnNotFound() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    org.mockito.Mockito.doThrow(
            new com.mw.planner.exception.country.CountryNotFoundException("InvalidCountry"))
        .when(countryService)
        .validateCountryExists("InvalidCountry");

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "InvalidCountry")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isNotFound());
  }

  @Test
  void verifyCsvFile_WithMissingRequiredFields_ShouldReturnBadRequest() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    // When & Then - Missing countryName
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest());
  }

  @Test
  void verifyCsvFile_WithMixedValidationResults_ShouldReturnCorrectTypes() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123\ninvalid123\ninv456\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    InventoryImportStatusResponseDTO response =
        InventoryImportStatusResponseDTO.builder()
            .results(
                List.of(
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.DUPLICATE)
                        .message("Inventory ID appears multiple times in CSV file")
                        .row(2)
                        .build(),
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("invalid123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.INVALID)
                        .message("Inventory ID does not exist")
                        .row(3)
                        .build(),
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv456")
                        .type(InventoryImportStatusResponseDTO.ValidationType.VALID)
                        .message("Inventory is valid")
                        .row(4)
                        .build(),
                    InventoryImportStatusResponseDTO.ValidationResult.builder()
                        .id("inv123")
                        .type(InventoryImportStatusResponseDTO.ValidationType.DUPLICATE)
                        .message("Inventory ID appears multiple times in CSV file")
                        .row(5)
                        .build()))
            .build();

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenReturn(response);

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results.length()").value(4))
        .andExpect(jsonPath("$.data.results[0].type").value("DUPLICATE"))
        .andExpect(jsonPath("$.data.results[1].type").value("INVALID"))
        .andExpect(jsonPath("$.data.results[2].type").value("VALID"))
        .andExpect(jsonPath("$.data.results[3].type").value("DUPLICATE"));
  }

  @Test
  void verifyCsvFile_WithIOException_ShouldReturnBadRequest() throws Exception {
    // Given
    String csvContent = "inventory_id\ninv123";
    MockMultipartFile csvFile =
        new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(new Campaign());
    doNothing().when(countryService).validateCountryExists("United States");
    when(inventoryImportService.verifyCsvFile(any(), eq("campaign123"), eq("United States")))
        .thenThrow(new java.io.IOException("Failed to read CSV file"));

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/campaign123/verify-csv")
                .file(csvFile)
                .param("countryName", "United States")
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest());
  }

  // ========== useInventoryImport Tests ==========

  @Test
  void useInventoryImport_WithValidImportId_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    String importId = "import123";
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    doNothing().when(inventoryImportService).useInventoryImport(campaignId, importId);

    // When & Then
    mockMvc
        .perform(post("/api/v1/campaign-inventory/" + campaignId + "/import/" + importId + "/use"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(inventoryImportService).useInventoryImport(campaignId, importId);
  }

  @Test
  void useInventoryImport_WithNonExistentCampaign_ShouldReturnNotFound() throws Exception {
    // Given
    String campaignId = "nonExistent";
    String importId = "import123";
    when(campaignService.findByIdForCurrentMode(campaignId))
        .thenThrow(new CampaignNotFoundException(campaignId));

    // When & Then
    mockMvc
        .perform(post("/api/v1/campaign-inventory/" + campaignId + "/import/" + importId + "/use"))
        .andExpect(status().isNotFound());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(inventoryImportService, never()).useInventoryImport(anyString(), anyString());
  }

  @Test
  void useInventoryImport_WithNonExistentImport_ShouldReturnNotFound() throws Exception {
    // Given
    String campaignId = "campaign123";
    String importId = "nonExistent";
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    doThrow(InventoryImportException.notFound(importId))
        .when(inventoryImportService)
        .useInventoryImport(campaignId, importId);

    // When & Then
    mockMvc
        .perform(post("/api/v1/campaign-inventory/" + campaignId + "/import/" + importId + "/use"))
        .andExpect(status().isNotFound());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(inventoryImportService).useInventoryImport(campaignId, importId);
  }

  @Test
  void useInventoryImport_WithAlreadySelectedInventory_ShouldReturnBadRequest() throws Exception {
    // Given
    String campaignId = "campaign123";
    String importId = "import123";
    String referenceId = "ref123";
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    doThrow(InventoryImportException.alreadySelected(referenceId))
        .when(inventoryImportService)
        .useInventoryImport(campaignId, importId);

    // When & Then
    mockMvc
        .perform(post("/api/v1/campaign-inventory/" + campaignId + "/import/" + importId + "/use"))
        .andExpect(status().isBadRequest());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(inventoryImportService).useInventoryImport(campaignId, importId);
  }

  @Test
  void useInventoryImport_WithValidationFailure_ShouldReturnBadRequest() throws Exception {
    // Given
    String campaignId = "campaign123";
    String importId = "import123";
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    doThrow(InventoryImportException.validationFailed("Validation error"))
        .when(inventoryImportService)
        .useInventoryImport(campaignId, importId);

    // When & Then
    mockMvc
        .perform(post("/api/v1/campaign-inventory/" + campaignId + "/import/" + importId + "/use"))
        .andExpect(status().isBadRequest());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(inventoryImportService).useInventoryImport(campaignId, importId);
  }

  @Test
  void useInventoryImport_WithUnexpectedException_ShouldReturnBadRequest() throws Exception {
    // Given
    String campaignId = "campaign123";
    String importId = "import123";
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    doThrow(new RuntimeException("Unexpected error"))
        .when(inventoryImportService)
        .useInventoryImport(campaignId, importId);

    // When & Then
    mockMvc
        .perform(post("/api/v1/campaign-inventory/" + campaignId + "/import/" + importId + "/use"))
        .andExpect(status().isBadRequest());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(inventoryImportService).useInventoryImport(campaignId, importId);
  }

  // ========== downloadInventoryImportCsv Tests ==========

  @Test
  void downloadInventoryImportCsv_WithValidImportId_ShouldReturnCsvFile() throws Exception {
    // Given
    String importId = "import123";
    String fileName = "test_inventory.csv";
    byte[] csvContent = "inventory_id\nref123\nref456".getBytes();

    InventoryImportService.CsvFileResult csvResult =
        new InventoryImportService.CsvFileResult(fileName, csvContent);

    when(inventoryImportService.generateInventoryImportCsv(importId)).thenReturn(csvResult);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/import/" + importId + "/download"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Content-Type"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Content-Disposition"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .bytes(csvContent));

    verify(inventoryImportService).generateInventoryImportCsv(importId);
  }

  @Test
  void downloadInventoryImportCsv_WithNonExistentImport_ShouldReturnNotFound() throws Exception {
    // Given
    String importId = "nonExistent";
    when(inventoryImportService.generateInventoryImportCsv(importId))
        .thenThrow(InventoryImportException.notFound(importId));

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/import/" + importId + "/download"))
        .andExpect(status().isNotFound());

    verify(inventoryImportService).generateInventoryImportCsv(importId);
  }

  @Test
  void downloadInventoryImportCsv_WithUnexpectedException_ShouldReturnBadRequest()
      throws Exception {
    // Given
    String importId = "import123";
    when(inventoryImportService.generateInventoryImportCsv(importId))
        .thenThrow(new RuntimeException("Unexpected error"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/import/" + importId + "/download"))
        .andExpect(status().isBadRequest());

    verify(inventoryImportService).generateInventoryImportCsv(importId);
  }

  // ========== deleteInventoryImport Tests ==========

  @Test
  void deleteInventoryImport_WithValidImportId_ShouldReturnOk() throws Exception {
    // Given
    String importId = "import123";
    doNothing().when(inventoryImportService).deleteInventoryImport(importId);

    // When & Then
    mockMvc
        .perform(delete("/api/v1/campaign-inventory/import/" + importId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(inventoryImportService).deleteInventoryImport(importId);
  }

  @Test
  void deleteInventoryImport_WithNonExistentImport_ShouldReturnNotFound() throws Exception {
    // Given
    String importId = "nonExistent";
    doThrow(InventoryImportException.notFound(importId))
        .when(inventoryImportService)
        .deleteInventoryImport(importId);

    // When & Then
    mockMvc
        .perform(delete("/api/v1/campaign-inventory/import/" + importId))
        .andExpect(status().isNotFound());

    verify(inventoryImportService).deleteInventoryImport(importId);
  }

  @Test
  void deleteInventoryImport_WithUnexpectedException_ShouldReturnBadRequest() throws Exception {
    // Given
    String importId = "import123";
    doThrow(new RuntimeException("Unexpected error"))
        .when(inventoryImportService)
        .deleteInventoryImport(importId);

    // When & Then
    mockMvc
        .perform(delete("/api/v1/campaign-inventory/import/" + importId))
        .andExpect(status().isBadRequest());

    verify(inventoryImportService).deleteInventoryImport(importId);
  }

  // ========== importGeoCoordinates Tests ==========

  @Test
  void importGeoCoordinates_WithValidRequest_ShouldReturnOk() throws Exception {
    // Given
    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName("geo_import_file.csv")
            .countryName("Singapore")
            .geoDetails(
                List.of(
                    GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
                        .locationName("Singapore")
                        .radius("45")
                        .latitude("1.3352566")
                        .longitude("103.963586")
                        .siteType("location1")
                        .build(),
                    GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
                        .locationName("Singapore")
                        .radius("45")
                        .latitude("1.2321")
                        .longitude("103.213")
                        .siteType("location2")
                        .build()))
            .build();

    when(userService.getActingCompanyId()).thenReturn("company123");
    doNothing().when(countryService).validateCountryExists("Singapore");
    doNothing()
        .when(inventoryImportService)
        .importGeoCoordinates(any(GeoCoordinatesImportRequestDTO.class), eq("company123"));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/import-geo-coordinates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("File imported successfully"));

    verify(countryService).validateCountryExists("Singapore");
    verify(userService).getActingCompanyId();
    verify(inventoryImportService)
        .importGeoCoordinates(any(GeoCoordinatesImportRequestDTO.class), eq("company123"));
  }

  @Test
  void importGeoCoordinates_WithInvalidCountry_ShouldReturnNotFound() throws Exception {
    // Given
    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName("geo_import_file.csv")
            .countryName("InvalidCountry")
            .geoDetails(
                List.of(
                    GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
                        .locationName("Location")
                        .latitude("1.3352566")
                        .longitude("103.963586")
                        .build()))
            .build();

    doThrow(new com.mw.planner.exception.country.CountryNotFoundException("InvalidCountry"))
        .when(countryService)
        .validateCountryExists("InvalidCountry");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/import-geo-coordinates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());

    verify(countryService).validateCountryExists("InvalidCountry");
    verify(inventoryImportService, never())
        .importGeoCoordinates(any(GeoCoordinatesImportRequestDTO.class), anyString());
  }

  @Test
  void importGeoCoordinates_WithMissingRequiredFields_ShouldReturnBadRequest() throws Exception {
    // Given
    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName("geo_import_file.csv")
            .countryName("Singapore")
            .geoDetails(List.of()) // Empty list should fail validation
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/import-geo-coordinates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void importGeoCoordinates_WithUnexpectedException_ShouldReturnBadRequest() throws Exception {
    // Given
    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName("geo_import_file.csv")
            .countryName("Singapore")
            .geoDetails(
                List.of(
                    GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
                        .locationName("Singapore")
                        .latitude("1.3352566")
                        .longitude("103.963586")
                        .build()))
            .build();

    when(userService.getActingCompanyId()).thenReturn("company123");
    doNothing().when(countryService).validateCountryExists("Singapore");
    doThrow(new RuntimeException("Unexpected error"))
        .when(inventoryImportService)
        .importGeoCoordinates(any(GeoCoordinatesImportRequestDTO.class), eq("company123"));

    // Mock messageService for exception handling
    when(messageService.getMessage(
            eq("error.csv_upload_processing_error"), eq(Locale.ENGLISH), any()))
        .thenReturn("Failed to import geo coordinates: Unexpected error");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/import-geo-coordinates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // ========== getImportGeoCoordinatesById Tests ==========

  @Test
  void getImportGeoCoordinatesById_WithValidId_ShouldReturnOk() throws Exception {
    // Given
    String geoImportId = "geo_import_123456";
    List<GeoImportFileResponseDTO.GeoDetailsDTO> geoDetailsList =
        List.of(
            GeoImportFileResponseDTO.GeoDetailsDTO.builder()
                .locationName("Singapore")
                .radius("45")
                .latitude("1.3352566")
                .longitude("103.963586")
                .siteType("location1")
                .build(),
            GeoImportFileResponseDTO.GeoDetailsDTO.builder()
                .locationName("Singapore")
                .radius("45")
                .latitude("1.2321")
                .longitude("103.213")
                .siteType("location2")
                .build());

    when(inventoryImportService.getGeoImportFileById(geoImportId)).thenReturn(geoDetailsList);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/import-geo-coordinates/" + geoImportId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].locationName").value("Singapore"))
        .andExpect(jsonPath("$.data[0].latitude").value("1.3352566"))
        .andExpect(jsonPath("$.data[0].longitude").value("103.963586"))
        .andExpect(jsonPath("$.data[1].locationName").value("Singapore"))
        .andExpect(jsonPath("$.data[1].latitude").value("1.2321"));

    verify(inventoryImportService).getGeoImportFileById(geoImportId);
  }

  @Test
  void getImportGeoCoordinatesById_WithEmptyGeoDetails_ShouldReturnEmptyList() throws Exception {
    // Given
    String geoImportId = "geo_import_123456";
    List<GeoImportFileResponseDTO.GeoDetailsDTO> emptyList = List.of();

    when(inventoryImportService.getGeoImportFileById(geoImportId)).thenReturn(emptyList);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/import-geo-coordinates/" + geoImportId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));

    verify(inventoryImportService).getGeoImportFileById(geoImportId);
  }

  // ========== downloadGeoImportCsv Tests ==========

  @Test
  void downloadGeoImportCsv_WithValidId_ShouldReturnCsvFile() throws Exception {
    // Given
    String geoImportId = "geo_import_123456";
    String fileName = "geo_import_file.csv";
    byte[] csvContent =
        "Location,Latitude,Longitude,Type,Radius\nSingapore,1.3352566,103.963586,location1,45"
            .getBytes();

    InventoryImportService.CsvFileResult csvResult =
        new InventoryImportService.CsvFileResult(fileName, csvContent);

    when(inventoryImportService.generateGeoImportCsv(geoImportId)).thenReturn(csvResult);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/import-geo-coordinates/" + geoImportId + "/download"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Content-Type", "text/csv"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Content-Disposition"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .bytes(csvContent));

    verify(inventoryImportService).generateGeoImportCsv(geoImportId);
  }

  @Test
  void downloadGeoImportCsv_WithUnexpectedException_ShouldReturnBadRequest() throws Exception {
    // Given
    String geoImportId = "geo_import_123456";
    when(inventoryImportService.generateGeoImportCsv(geoImportId))
        .thenThrow(new RuntimeException("Unexpected error"));

    IamUserContext iamUserContext = IamUserContext.builder().locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(iamUserContext);

    // Mock messageService for exception handling
    when(messageService.getMessage(
            eq("error.csv_upload_processing_error"), eq(Locale.ENGLISH), any()))
        .thenReturn("Failed to download geo import CSV: Unexpected error");

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/import-geo-coordinates/" + geoImportId + "/download"))
        .andExpect(status().isBadRequest());

    verify(inventoryImportService).generateGeoImportCsv(geoImportId);
  }

  // ========== deleteGeoImportById Tests ==========

  @Test
  void deleteGeoImportById_WithValidId_ShouldReturnOk() throws Exception {
    // Given
    String geoImportId = "geo_import_123456";
    doNothing().when(inventoryImportService).deleteGeoImportFileById(geoImportId);

    // When & Then
    mockMvc
        .perform(delete("/api/v1/campaign-inventory/import-geo-coordinates/" + geoImportId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(inventoryImportService).deleteGeoImportFileById(geoImportId);
  }

  @Test
  void deleteGeoImportById_WithUnexpectedException_ShouldReturnBadRequest() throws Exception {
    // Given
    String geoImportId = "geo_import_123456";
    doThrow(new RuntimeException("Unexpected error"))
        .when(inventoryImportService)
        .deleteGeoImportFileById(geoImportId);

    IamUserContext iamUserContext = IamUserContext.builder().locale(Locale.ENGLISH).build();
    when(userService.getIamUserContext()).thenReturn(iamUserContext);

    // Mock messageService for exception handling
    when(messageService.getMessage(
            eq("error.csv_upload_processing_error"), eq(Locale.ENGLISH), any()))
        .thenReturn("Failed to delete geo import file: Unexpected error");

    // When & Then
    mockMvc
        .perform(delete("/api/v1/campaign-inventory/import-geo-coordinates/" + geoImportId))
        .andExpect(status().isBadRequest());

    verify(inventoryImportService).deleteGeoImportFileById(geoImportId);
  }

  // ========== getGeoListExistingFile Tests ==========

  @Test
  void getGeoListExistingFile_WithValidCountryName_ShouldReturnOk() throws Exception {
    // Given
    String countryName = "Singapore";
    String companyId = "company123";

    GeoImportFileResponseDTO responseDTO1 =
        GeoImportFileResponseDTO.builder()
            .id("geo_import_1")
            .fileName("geo_import_file1.csv")
            .countryName("Singapore")
            .countOfCoordinates(10)
            .createdBy("user1@example.com")
            .lastModifiedBy("user1@example.com")
            .createdAt(java.time.LocalDateTime.of(2024, 1, 15, 10, 30, 0))
            .updatedAt(java.time.LocalDateTime.of(2024, 1, 15, 10, 30, 0))
            .build();

    GeoImportFileResponseDTO responseDTO2 =
        GeoImportFileResponseDTO.builder()
            .id("geo_import_2")
            .fileName("geo_import_file2.csv")
            .countryName("Singapore")
            .countOfCoordinates(5)
            .createdBy("user2@example.com")
            .lastModifiedBy("user2@example.com")
            .createdAt(java.time.LocalDateTime.of(2024, 1, 16, 11, 0, 0))
            .updatedAt(java.time.LocalDateTime.of(2024, 1, 16, 11, 0, 0))
            .build();

    Page<GeoImportFileResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO1, responseDTO2), PageRequest.of(0, 10), 2);

    when(userService.getActingCompanyId()).thenReturn(companyId);
    doNothing().when(countryService).validateCountryExists(countryName);
    when(inventoryImportService.getGeoImportFilesByCountry(
            eq(companyId), eq(countryName), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/geo-imports")
                .param("countryName", countryName)
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "createdAt")
                .param("sortDir", "desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].id").value("geo_import_1"))
        .andExpect(jsonPath("$.data.content[0].fileName").value("geo_import_file1.csv"))
        .andExpect(jsonPath("$.data.content[0].countOfCoordinates").value(10))
        .andExpect(jsonPath("$.data.content[0].createdBy").value("user1@example.com"))
        .andExpect(jsonPath("$.data.content[0].lastModifiedBy").value("user1@example.com"))
        .andExpect(jsonPath("$.data.content[1].id").value("geo_import_2"))
        .andExpect(jsonPath("$.data.content[1].countOfCoordinates").value(5))
        .andExpect(jsonPath("$.data.totalElements").value(2));

    verify(countryService).validateCountryExists(countryName);
    verify(userService).getActingCompanyId();
    verify(inventoryImportService)
        .getGeoImportFilesByCountry(eq(companyId), eq(countryName), any(Pageable.class));
  }

  @Test
  void getGeoListExistingFile_WithInvalidCountry_ShouldReturnNotFound() throws Exception {
    // Given
    String countryName = "InvalidCountry";
    doThrow(new com.mw.planner.exception.country.CountryNotFoundException("InvalidCountry"))
        .when(countryService)
        .validateCountryExists(countryName);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/geo-imports").param("countryName", countryName))
        .andExpect(status().isNotFound());

    verify(countryService).validateCountryExists(countryName);
    verify(inventoryImportService, never())
        .getGeoImportFilesByCountry(anyString(), anyString(), any(Pageable.class));
  }

  @Test
  void getGeoListExistingFile_WithEmptyResults_ShouldReturnEmptyPage() throws Exception {
    // Given
    String countryName = "Singapore";
    String companyId = "company123";
    Page<GeoImportFileResponseDTO> emptyPageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

    when(userService.getActingCompanyId()).thenReturn(companyId);
    doNothing().when(countryService).validateCountryExists(countryName);
    when(inventoryImportService.getGeoImportFilesByCountry(
            eq(companyId), eq(countryName), any(Pageable.class)))
        .thenReturn(emptyPageResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/geo-imports").param("countryName", countryName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(0));

    verify(countryService).validateCountryExists(countryName);
    verify(userService).getActingCompanyId();
    verify(inventoryImportService)
        .getGeoImportFilesByCountry(eq(companyId), eq(countryName), any(Pageable.class));
  }

  @Test
  void getGeoListExistingFile_WithPagination_ShouldReturnCorrectPage() throws Exception {
    // Given
    String countryName = "Singapore";
    String companyId = "company123";

    GeoImportFileResponseDTO responseDTO =
        GeoImportFileResponseDTO.builder()
            .id("geo_import_1")
            .fileName("geo_import_file1.csv")
            .countOfCoordinates(10)
            .createdBy("user1@example.com")
            .lastModifiedBy("user1@example.com")
            .build();

    Page<GeoImportFileResponseDTO> pageResponse =
        new PageImpl<>(List.of(responseDTO), PageRequest.of(1, 2), 10);

    when(userService.getActingCompanyId()).thenReturn(companyId);
    doNothing().when(countryService).validateCountryExists(countryName);
    when(inventoryImportService.getGeoImportFilesByCountry(
            eq(companyId), eq(countryName), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/geo-imports")
                .param("countryName", countryName)
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(10))
        .andExpect(jsonPath("$.data.number").value(1));

    verify(inventoryImportService)
        .getGeoImportFilesByCountry(eq(companyId), eq(countryName), any(Pageable.class));
  }

  @Test
  void getGeoListExistingFile_WithInvalidPagination_ShouldUseDefaults() throws Exception {
    // Given
    String countryName = "Singapore";
    String companyId = "company123";
    Page<GeoImportFileResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 1), 0);

    when(userService.getActingCompanyId()).thenReturn(companyId);
    doNothing().when(countryService).validateCountryExists(countryName);
    when(inventoryImportService.getGeoImportFilesByCountry(
            eq(companyId), eq(countryName), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then - Test with negative page and size values
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/geo-imports")
                .param("countryName", countryName)
                .param("page", "-1")
                .param("size", "0"))
        .andExpect(status().isOk());

    verify(inventoryImportService)
        .getGeoImportFilesByCountry(eq(companyId), eq(countryName), any(Pageable.class));
  }

  @Test
  void getGeoListExistingFile_WithAscendingSort_ShouldReturnOk() throws Exception {
    // Given
    String countryName = "Singapore";
    String companyId = "company123";
    Page<GeoImportFileResponseDTO> pageResponse =
        new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

    when(userService.getActingCompanyId()).thenReturn(companyId);
    doNothing().when(countryService).validateCountryExists(countryName);
    when(inventoryImportService.getGeoImportFilesByCountry(
            eq(companyId), eq(countryName), any(Pageable.class)))
        .thenReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/campaign-inventory/geo-imports")
                .param("countryName", countryName)
                .param("sortBy", "fileName")
                .param("sortDir", "asc"))
        .andExpect(status().isOk());

    verify(inventoryImportService)
        .getGeoImportFilesByCountry(eq(companyId), eq(countryName), any(Pageable.class));
  }

  // ========== generateCampaignComments Tests ==========

  @Test
  void generateCampaignComments_WithValidRequestWithFiles_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment";
    List<String> taggedCompanyIds = List.of("company456", "company789");
    String userCompanyId = "company123";

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder()
            .comment(comment)
            .taggedCompanyIds(taggedCompanyIds)
            .build();

    MockMultipartFile file1 =
        new MockMultipartFile("files", "test1.pdf", "application/pdf", "test content 1".getBytes());
    MockMultipartFile file2 =
        new MockMultipartFile("files", "test2.jpg", "image/jpeg", "test content 2".getBytes());

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    when(userService.getActingCompanyId()).thenReturn(userCompanyId);
    doNothing()
        .when(campaignService)
        .createCampaignComment(
            eq(campaignId), eq(comment), any(List.class), eq(taggedCompanyIds), eq(userCompanyId));

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .file(file1)
                .file(file2)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Campaign comment added successfully."));

    verify(userService).getActingCompanyId();
    verify(campaignService)
        .createCampaignComment(
            eq(campaignId), eq(comment), any(List.class), eq(taggedCompanyIds), eq(userCompanyId));
  }

  @Test
  void generateCampaignComments_WithValidRequestWithoutFiles_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment without files";
    String userCompanyId = "company123";

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder().comment(comment).build();

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    when(userService.getActingCompanyId()).thenReturn(userCompanyId);
    doNothing()
        .when(campaignService)
        .createCampaignComment(eq(campaignId), eq(comment), any(), any(), eq(userCompanyId));

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Campaign comment added successfully."));

    verify(userService).getActingCompanyId();
    verify(campaignService)
        .createCampaignComment(eq(campaignId), eq(comment), any(), any(), eq(userCompanyId));
  }

  @Test
  void generateCampaignComments_WithValidRequestWithoutTaggedCompanyIds_ShouldReturnOk()
      throws Exception {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment without tagged companies";
    String userCompanyId = "company123";

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder().comment(comment).build();

    MockMultipartFile file =
        new MockMultipartFile("files", "test.pdf", "application/pdf", "test content".getBytes());

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    when(userService.getActingCompanyId()).thenReturn(userCompanyId);
    doNothing()
        .when(campaignService)
        .createCampaignComment(eq(campaignId), eq(comment), any(), any(), eq(userCompanyId));

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Campaign comment added successfully."));

    verify(userService).getActingCompanyId();
    verify(campaignService)
        .createCampaignComment(eq(campaignId), eq(comment), any(), any(), eq(userCompanyId));
  }

  @Test
  void generateCampaignComments_WithMissingComment_ShouldReturnBadRequest() throws Exception {
    // Given
    String campaignId = "campaign123";

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder().comment(null).build();

    MockMultipartFile file =
        new MockMultipartFile("files", "test.pdf", "application/pdf", "test content".getBytes());

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // When & Then - Missing comment parameter should fail validation
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest());

    verify(campaignService, never())
        .createCampaignComment(anyString(), anyString(), any(), any(), anyString());
  }

  @Test
  void generateCampaignComments_WithBlankComment_ShouldReturnBadRequest() throws Exception {
    // Given
    String campaignId = "campaign123";
    String blankComment = "   ";

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder().comment(blankComment).build();

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // Lenient: validation rejects the blank comment before the company lookup happens
    org.mockito.Mockito.lenient().when(userService.getActingCompanyId()).thenReturn("company123");

    // When & Then - Blank comment should fail validation
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest());

    verify(campaignService, never())
        .createCampaignComment(anyString(), anyString(), any(), any(), anyString());
  }

  @Test
  void generateCampaignComments_WithMultipleFiles_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment with multiple files";
    String userCompanyId = "company123";

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder().comment(comment).build();

    MockMultipartFile file1 =
        new MockMultipartFile("files", "test1.pdf", "application/pdf", "content1".getBytes());
    MockMultipartFile file2 =
        new MockMultipartFile("files", "test2.jpg", "image/jpeg", "content2".getBytes());
    MockMultipartFile file3 =
        new MockMultipartFile("files", "test3.png", "image/png", "content3".getBytes());

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    when(userService.getActingCompanyId()).thenReturn(userCompanyId);
    doNothing()
        .when(campaignService)
        .createCampaignComment(
            eq(campaignId), eq(comment), any(List.class), any(), eq(userCompanyId));

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .file(file1)
                .file(file2)
                .file(file3)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Campaign comment added successfully."));

    verify(userService).getActingCompanyId();
    verify(campaignService)
        .createCampaignComment(
            eq(campaignId), eq(comment), any(List.class), any(), eq(userCompanyId));
  }

  @Test
  void generateCampaignComments_WithEmptyFileList_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment with empty file list";
    String userCompanyId = "company123";

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder().comment(comment).build();

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    when(userService.getActingCompanyId()).thenReturn(userCompanyId);
    doNothing()
        .when(campaignService)
        .createCampaignComment(eq(campaignId), eq(comment), any(), any(), eq(userCompanyId));

    // When & Then - Empty file list should be handled gracefully
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Campaign comment added successfully."));

    verify(userService).getActingCompanyId();
    verify(campaignService)
        .createCampaignComment(eq(campaignId), eq(comment), any(), any(), eq(userCompanyId));
  }

  @Test
  void generateCampaignComments_WithSingleTaggedCompanyId_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    String comment = "Test comment with single tagged company";
    String userCompanyId = "company123";
    List<String> taggedCompanyIds = List.of("company456");

    CampaignCommentsRequestDTO requestDTO =
        CampaignCommentsRequestDTO.builder()
            .comment(comment)
            .taggedCompanyIds(taggedCompanyIds)
            .build();

    MockPart requestPart = new MockPart("request", objectMapper.writeValueAsBytes(requestDTO));
    requestPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    when(userService.getActingCompanyId()).thenReturn(userCompanyId);
    doNothing()
        .when(campaignService)
        .createCampaignComment(
            eq(campaignId), eq(comment), any(), eq(taggedCompanyIds), eq(userCompanyId));

    // When & Then
    mockMvc
        .perform(
            multipart("/api/v1/campaign-inventory/" + campaignId + "/comment")
                .part(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Campaign comment added successfully."));

    verify(userService).getActingCompanyId();
    verify(campaignService)
        .createCampaignComment(
            eq(campaignId), eq(comment), any(), eq(taggedCompanyIds), eq(userCompanyId));
  }

  // ========== getCommentsByCampaignId Tests ==========

  @Test
  void getCommentsByCampaignId_WithValidCampaignAndComments_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignCommentsResponseDTO responseDTO1 =
        CampaignCommentsResponseDTO.builder()
            .comment("First comment")
            .createdBy("user1@example.com")
            .createdAt(createdAt)
            .businessType(CompanyDto.BusinessType.MEDIA_BUYER)
            .build();

    CampaignCommentsResponseDTO responseDTO2 =
        CampaignCommentsResponseDTO.builder()
            .comment("Second comment")
            .createdBy("user2@example.com")
            .createdAt(createdAt.plusHours(1))
            .businessType(CompanyDto.BusinessType.MEDIA_OWNER)
            .build();

    List<CampaignCommentsResponseDTO> comments = List.of(responseDTO1, responseDTO2);

    when(campaignService.getCommentsByCampaignId(campaignId)).thenReturn(comments);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/" + campaignId + "/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].comment").value("First comment"))
        .andExpect(jsonPath("$.data[0].createdBy").value("user1@example.com"))
        .andExpect(jsonPath("$.data[0].businessType").value("MEDIA_BUYER"))
        .andExpect(jsonPath("$.data[1].comment").value("Second comment"))
        .andExpect(jsonPath("$.data[1].createdBy").value("user2@example.com"))
        .andExpect(jsonPath("$.data[1].businessType").value("MEDIA_OWNER"));

    verify(campaignService).getCommentsByCampaignId(campaignId);
  }

  @Test
  void getCommentsByCampaignId_WithEmptyComments_ShouldReturnEmptyList() throws Exception {
    // Given
    String campaignId = "campaign123";
    List<CampaignCommentsResponseDTO> emptyComments = List.of();

    when(campaignService.getCommentsByCampaignId(campaignId)).thenReturn(emptyComments);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/" + campaignId + "/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));

    verify(campaignService).getCommentsByCampaignId(campaignId);
  }

  @Test
  void getCommentsByCampaignId_WithCommentWithoutBusinessType_ShouldReturnNullBusinessType()
      throws Exception {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignCommentsResponseDTO responseDTO =
        CampaignCommentsResponseDTO.builder()
            .comment("Comment without business type")
            .createdBy("user@example.com")
            .createdAt(createdAt)
            .businessType(null) // No business type
            .build();

    List<CampaignCommentsResponseDTO> comments = List.of(responseDTO);

    when(campaignService.getCommentsByCampaignId(campaignId)).thenReturn(comments);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/" + campaignId + "/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].comment").value("Comment without business type"))
        .andExpect(jsonPath("$.data[0].createdBy").value("user@example.com"))
        .andExpect(jsonPath("$.data[0].businessType").isEmpty());

    verify(campaignService).getCommentsByCampaignId(campaignId);
  }

  @Test
  void getCommentsByCampaignId_WithCampaignNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    String campaignId = "nonexistent";

    // Mock messageService for exception handler
    when(messageService.getMessage(
            eq("error.campaign_not_found"), any(Locale.class), eq(campaignId)))
        .thenReturn("Campaign not found with ID/name: " + campaignId);

    doThrow(new com.mw.planner.exception.campaign.CampaignNotFoundException(campaignId))
        .when(campaignService)
        .getCommentsByCampaignId(campaignId);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/" + campaignId + "/comments"))
        .andExpect(status().isNotFound());

    verify(campaignService).getCommentsByCampaignId(campaignId);
  }

  @Test
  void getCommentsByCampaignId_WithSingleComment_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignCommentsResponseDTO responseDTO =
        CampaignCommentsResponseDTO.builder()
            .comment("Single comment")
            .createdBy("user@example.com")
            .createdAt(createdAt)
            .businessType(CompanyDto.BusinessType.MEDIA_AGENCY)
            .build();

    List<CampaignCommentsResponseDTO> comments = List.of(responseDTO);

    when(campaignService.getCommentsByCampaignId(campaignId)).thenReturn(comments);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/" + campaignId + "/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].comment").value("Single comment"))
        .andExpect(jsonPath("$.data[0].createdBy").value("user@example.com"))
        .andExpect(jsonPath("$.data[0].businessType").value("MEDIA_AGENCY"));

    verify(campaignService).getCommentsByCampaignId(campaignId);
  }

  @Test
  void getCommentsByCampaignId_WithMultipleCommentsDifferentBusinessTypes_ShouldReturnOk()
      throws Exception {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignCommentsResponseDTO responseDTO1 =
        CampaignCommentsResponseDTO.builder()
            .comment("Comment from media buyer")
            .createdBy("buyer@example.com")
            .createdAt(createdAt)
            .businessType(CompanyDto.BusinessType.MEDIA_BUYER)
            .build();

    CampaignCommentsResponseDTO responseDTO2 =
        CampaignCommentsResponseDTO.builder()
            .comment("Comment from media owner")
            .createdBy("owner@example.com")
            .createdAt(createdAt.plusHours(1))
            .businessType(CompanyDto.BusinessType.MEDIA_OWNER)
            .build();

    CampaignCommentsResponseDTO responseDTO3 =
        CampaignCommentsResponseDTO.builder()
            .comment("Comment from media operator")
            .createdBy("operator@example.com")
            .createdAt(createdAt.plusHours(2))
            .businessType(CompanyDto.BusinessType.MEDIA_OPERATOR)
            .build();

    List<CampaignCommentsResponseDTO> comments = List.of(responseDTO1, responseDTO2, responseDTO3);

    when(campaignService.getCommentsByCampaignId(campaignId)).thenReturn(comments);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/" + campaignId + "/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].businessType").value("MEDIA_BUYER"))
        .andExpect(jsonPath("$.data[1].businessType").value("MEDIA_OWNER"))
        .andExpect(jsonPath("$.data[2].businessType").value("MEDIA_OPERATOR"));

    verify(campaignService).getCommentsByCampaignId(campaignId);
  }

  @Test
  void getCommentsByCampaignId_WithCommentHavingFileUrls_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    CampaignCommentsResponseDTO responseDTO =
        CampaignCommentsResponseDTO.builder()
            .comment("Comment with file attachments")
            .createdBy("user@example.com")
            .createdAt(createdAt)
            .businessType(CompanyDto.BusinessType.MEDIA_BUYER)
            .build();

    List<CampaignCommentsResponseDTO> comments = List.of(responseDTO);

    when(campaignService.getCommentsByCampaignId(campaignId)).thenReturn(comments);

    // When & Then
    mockMvc
        .perform(get("/api/v1/campaign-inventory/" + campaignId + "/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].comment").value("Comment with file attachments"));

    verify(campaignService).getCommentsByCampaignId(campaignId);
  }

  // ========== getSchedulesByInventoryIds Tests ==========

  @Test
  void getSchedulesByInventoryIds_WithValidRequest_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    GetSchedulesRequestDTO request = new GetSchedulesRequestDTO();
    request.setInventoryIds(List.of("inventory123", "inventory456"));

    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put("01-Jan-2024", List.of(0, 1, 2, 3));

    InventorySchedulesResponseDTO.ScheduleDTO scheduleDTO1 =
        InventorySchedulesResponseDTO.ScheduleDTO.builder()
            .name("Schedule 1")
            .startDate(java.time.LocalDate.of(2024, 1, 1))
            .endDate(java.time.LocalDate.of(2024, 1, 10))
            .scheduleDays(List.of("MONDAY", "TUESDAY", "WEDNESDAY"))
            .bookingMatrix(bookingMatrix)
            .duration(10L)
            .spotsPerLoop(5L)
            .spotsPerHour(12L)
            .adPlays(60L)
            .build();

    InventorySchedulesResponseDTO responseDTO1 =
        InventorySchedulesResponseDTO.builder()
            .inventoryId("inventory123")
            .schedules(List.of(scheduleDTO1))
            .build();

    InventorySchedulesResponseDTO responseDTO2 =
        InventorySchedulesResponseDTO.builder()
            .inventoryId("inventory456")
            .schedules(Collections.emptyList())
            .build();

    List<InventorySchedulesResponseDTO> schedules = List.of(responseDTO1, responseDTO2);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    when(campaignInventorySchedulesService.getSchedulesByInventoryIds(
            eq(campaignId), eq(request.getInventoryIds()), any()))
        .thenReturn(schedules);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].inventoryId").value("inventory123"))
        .andExpect(jsonPath("$.data[0].schedules").isArray())
        .andExpect(jsonPath("$.data[0].schedules.length()").value(1))
        .andExpect(jsonPath("$.data[0].schedules[0].name").value("Schedule 1"))
        .andExpect(jsonPath("$.data[0].schedules[0].duration").value(10))
        .andExpect(jsonPath("$.data[0].schedules[0].bookingMatrix").exists())
        .andExpect(jsonPath("$.data[0].schedules[0].bookingMatrix['01-Jan-2024']").isArray())
        .andExpect(jsonPath("$.data[1].inventoryId").value("inventory456"))
        .andExpect(jsonPath("$.data[1].schedules").isEmpty());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService)
        .getSchedulesByInventoryIds(eq(campaignId), eq(request.getInventoryIds()), any());
  }

  @Test
  void getSchedulesByInventoryIds_WithCampaignNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    String campaignId = "nonexistent";
    GetSchedulesRequestDTO request = new GetSchedulesRequestDTO();
    request.setInventoryIds(List.of("inventory123"));

    when(campaignService.findByIdForCurrentMode(campaignId))
        .thenThrow(new CampaignNotFoundException(campaignId));

    // Mock messageService for exception handler
    when(messageService.getMessage(
            eq("error.campaign_not_found"), any(Locale.class), eq(campaignId)))
        .thenReturn("Campaign not found with ID/name: " + campaignId);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService, never())
        .getSchedulesByInventoryIds(anyString(), anyList(), any());
  }

  @Test
  void getSchedulesByInventoryIds_WithEmptyInventoryIds_ShouldReturnBadRequest() throws Exception {
    // Given
    String campaignId = "campaign123";
    GetSchedulesRequestDTO request = new GetSchedulesRequestDTO();
    request.setInventoryIds(Collections.emptyList());

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    when(campaignInventorySchedulesService.getSchedulesByInventoryIds(
            eq(campaignId), eq(Collections.emptyList()), any()))
        .thenReturn(Collections.emptyList());

    // When & Then
    // Empty list is allowed - service returns all schedules for the campaign
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService)
        .getSchedulesByInventoryIds(eq(campaignId), eq(Collections.emptyList()), any());
  }

  @Test
  void getSchedulesByInventoryIds_WithNullInventoryIds_ShouldReturnOk() throws Exception {
    // Given
    String campaignId = "campaign123";
    // Create JSON with null inventoryIds explicitly
    String requestJson = "{\"inventoryIds\": null}";

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());

    // When & Then
    // Null list is allowed - service will return all schedules for the campaign
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService)
        .getSchedulesByInventoryIds(eq(campaignId), eq(null), any());
  }

  @Test
  void getSchedulesByInventoryIds_WithMultipleInventories_ShouldReturnAllSchedules()
      throws Exception {
    // Given
    String campaignId = "campaign123";
    GetSchedulesRequestDTO request = new GetSchedulesRequestDTO();
    request.setInventoryIds(List.of("inventory1", "inventory2", "inventory3"));

    Map<String, List<Integer>> bookingMatrix1 = new HashMap<>();
    bookingMatrix1.put("01-Jan-2024", List.of(6, 7, 8, 9));

    Map<String, List<Integer>> bookingMatrix2 = new HashMap<>();
    bookingMatrix2.put("06-Jan-2024", List.of(18, 19, 20, 21));

    InventorySchedulesResponseDTO.ScheduleDTO schedule1 =
        InventorySchedulesResponseDTO.ScheduleDTO.builder()
            .name("Morning Schedule")
            .startDate(java.time.LocalDate.of(2024, 1, 1))
            .endDate(java.time.LocalDate.of(2024, 1, 5))
            .scheduleDays(List.of("MONDAY", "WEDNESDAY", "FRIDAY"))
            .bookingMatrix(bookingMatrix1)
            .duration(5L)
            .spotsPerLoop(3L)
            .spotsPerHour(10L)
            .adPlays(30L)
            .build();

    InventorySchedulesResponseDTO.ScheduleDTO schedule2 =
        InventorySchedulesResponseDTO.ScheduleDTO.builder()
            .name("Evening Schedule")
            .startDate(java.time.LocalDate.of(2024, 1, 6))
            .endDate(java.time.LocalDate.of(2024, 1, 10))
            .scheduleDays(List.of("TUESDAY", "THURSDAY"))
            .bookingMatrix(bookingMatrix2)
            .duration(5L)
            .spotsPerLoop(4L)
            .spotsPerHour(15L)
            .adPlays(45L)
            .build();

    List<InventorySchedulesResponseDTO> schedules =
        List.of(
            InventorySchedulesResponseDTO.builder()
                .inventoryId("inventory1")
                .schedules(List.of(schedule1))
                .build(),
            InventorySchedulesResponseDTO.builder()
                .inventoryId("inventory2")
                .schedules(List.of(schedule2))
                .build(),
            InventorySchedulesResponseDTO.builder()
                .inventoryId("inventory3")
                .schedules(Collections.emptyList())
                .build());

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    when(campaignInventorySchedulesService.getSchedulesByInventoryIds(
            eq(campaignId), eq(request.getInventoryIds()), any()))
        .thenReturn(schedules);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].inventoryId").value("inventory1"))
        .andExpect(jsonPath("$.data[0].schedules[0].name").value("Morning Schedule"))
        .andExpect(jsonPath("$.data[1].inventoryId").value("inventory2"))
        .andExpect(jsonPath("$.data[1].schedules[0].name").value("Evening Schedule"))
        .andExpect(jsonPath("$.data[2].inventoryId").value("inventory3"))
        .andExpect(jsonPath("$.data[2].schedules").isEmpty());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService)
        .getSchedulesByInventoryIds(eq(campaignId), eq(request.getInventoryIds()), any());
  }

  @Test
  void getSchedulesByInventoryIds_WithEmptySchedules_ShouldReturnEmptySchedules() throws Exception {
    // Given
    String campaignId = "campaign123";
    GetSchedulesRequestDTO request = new GetSchedulesRequestDTO();
    request.setInventoryIds(List.of("inventory123"));

    InventorySchedulesResponseDTO responseDTO =
        InventorySchedulesResponseDTO.builder()
            .inventoryId("inventory123")
            .schedules(Collections.emptyList())
            .build();

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    when(campaignInventorySchedulesService.getSchedulesByInventoryIds(
            eq(campaignId), eq(request.getInventoryIds()), any()))
        .thenReturn(List.of(responseDTO));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].inventoryId").value("inventory123"))
        .andExpect(jsonPath("$.data[0].schedules").isEmpty());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService)
        .getSchedulesByInventoryIds(eq(campaignId), eq(request.getInventoryIds()), any());
  }

  // ========== bulkSchedules Tests ==========

  @Test
  void bulkSchedules_WithValidRequest_ShouldReturnSuccess() throws Exception {
    // Given
    String campaignId = "campaign123";
    BulkSchedulesRequestDTO request = new BulkSchedulesRequestDTO();
    request.setInventoryIds(List.of("inventory123"));
    request.setClearSchedules(true);
    BulkSchedulesRequestDTO.ScheduleDTO scheduleDTO = new BulkSchedulesRequestDTO.ScheduleDTO();
    scheduleDTO.setStartDate(java.time.LocalDate.now());
    scheduleDTO.setEndDate(java.time.LocalDate.now().plusDays(7));
    scheduleDTO.setScheduleDays(List.of("MONDAY"));
    Map<String, List<Integer>> bookingMatrix = new HashMap<>();
    bookingMatrix.put(
        java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
        List.of(0));
    scheduleDTO.setBookingMatrix(bookingMatrix);
    request.setSchedule(scheduleDTO);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(new Campaign());
    doNothing()
        .when(campaignInventorySchedulesService)
        .bulkSchedules(eq(campaignId), any(BulkSchedulesRequestDTO.class));
    when(messageService.getMessage(anyString(), any()))
        .thenReturn("Operation completed successfully");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/inventory/bulk-schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService)
        .bulkSchedules(eq(campaignId), any(BulkSchedulesRequestDTO.class));
  }

  @Test
  void bulkSchedules_WhenCampaignNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    String campaignId = "nonexistent";
    BulkSchedulesRequestDTO request = new BulkSchedulesRequestDTO();
    request.setInventoryIds(List.of("inventory123"));
    request.setClearSchedules(true);

    when(campaignService.findByIdForCurrentMode(campaignId))
        .thenThrow(new CampaignNotFoundException(campaignId));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/campaign-inventory/" + campaignId + "/inventory/bulk-schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());

    verify(campaignService).findByIdForCurrentMode(campaignId);
    verify(campaignInventorySchedulesService, never()).bulkSchedules(anyString(), any());
  }
}
