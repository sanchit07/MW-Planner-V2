package com.mw.planner.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.CampaignForecastDTO;
import com.mw.planner.dto.InventoryResponseDTO;
import com.mw.planner.dto.recommendation.*;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.InventoryService;
import com.mw.planner.service.ScheduleCacheEvictor;
import com.mw.planner.service.VenuesService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @Mock private RecommendationEngineApiClient apiClient;
  @Mock private CampaignRepository campaignRepository;
  @Mock private CampaignInventorySchedulesRepository inventorySchedulesRepository;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private CampaignService campaignService;
  @Mock private InventoryService inventoryService;
  @Mock private VenuesService venuesService;
  @Mock private ScheduleCacheEvictor scheduleCacheEvictor;

  @InjectMocks private RecommendationService recommendationService;

  private Campaign testCampaign;
  private RecommendationApiResponse<PaginatedRecommendationResponseDTO> apiResponse;
  private PaginatedRecommendationResponseDTO responseDTO;

  @BeforeEach
  void setUp() {
    testCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    testCampaign.setId("campaign123");

    responseDTO = new PaginatedRecommendationResponseDTO();
    responseDTO.setRunId("run123");
    responseDTO.setCampaignId("campaign123");

    apiResponse = new RecommendationApiResponse<>();
    apiResponse.setSuccess(true);
    apiResponse.setData(responseDTO);
  }

  @Test
  void getRecommendationResults_shouldFilterOutRecommendationsWithNullInternalId() {
    // Given
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();

    // Recommendation with valid internal ID
    PaginatedRecommendationResponseDTO.RecommendedInventory validRec =
        createRecommendation("inv1", "ext1", "UNSELECTED");
    recommendations.add(validRec);

    // Recommendation with null internal ID (inventory not found in DB)
    PaginatedRecommendationResponseDTO.RecommendedInventory nullInternalIdRec =
        createRecommendation("inv2", "ext2", "UNSELECTED");
    recommendations.add(nullInternalIdRec);

    // Recommendation with null inventoryDetails
    PaginatedRecommendationResponseDTO.RecommendedInventory nullDetailsRec =
        new PaginatedRecommendationResponseDTO.RecommendedInventory();
    nullDetailsRec.setInventoryId("ext3");
    nullDetailsRec.setSelectionMode("UNSELECTED");
    nullDetailsRec.setInventoryDetails(null);
    recommendations.add(nullDetailsRec);

    responseDTO.setRecommendations(recommendations);

    // Mock API client response
    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);

    // Mock campaign lookup
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    // Mock inventory lookup - only ext1 exists in DB
    Inventory inventory1 = new Inventory();
    inventory1.setId("internal1");
    inventory1.setExternalId("ext1");

    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory1));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getRecommendations()).hasSize(1);
    assertThat(result.getRecommendations().get(0).getInventoryDetails().getInternalId())
        .isEqualTo("internal1");

    // Verify logging was called for filtered items
    verify(inventoryService).findByExternalIdIn(anyList());
  }

  @Test
  void getRecommendationResults_shouldNotFilterWhenAllHaveInternalIds() {
    // Given
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();
    recommendations.add(createRecommendation("inv1", "ext1", "UNSELECTED"));
    recommendations.add(createRecommendation("inv2", "ext2", "UNSELECTED"));
    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    // Both inventories exist in DB
    Inventory inventory1 = new Inventory();
    inventory1.setId("internal1");
    inventory1.setExternalId("ext1");

    Inventory inventory2 = new Inventory();
    inventory2.setId("internal2");
    inventory2.setExternalId("ext2");

    when(inventoryService.findByExternalIdIn(anyList()))
        .thenReturn(Arrays.asList(inventory1, inventory2));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getRecommendations()).hasSize(2);
    assertThat(result.getRecommendations().get(0).getInventoryDetails().getInternalId())
        .isEqualTo("internal1");
    assertThat(result.getRecommendations().get(1).getInventoryDetails().getInternalId())
        .isEqualTo("internal2");
  }

  @Test
  void getRecommendationResults_shouldHandleEmptyRecommendationsList() {
    // Given
    responseDTO.setRecommendations(Collections.emptyList());

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getRecommendations()).isEmpty();
    verify(campaignRepository, never()).findById(anyString());
    verify(inventoryService, never()).findByExternalIdIn(anyList());
  }

  @Test
  void getRecommendationResults_shouldHandleNullRecommendationsList() {
    // Given
    responseDTO.setRecommendations(null);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getRecommendations()).isNull();
    verify(campaignRepository, never()).findById(anyString());
    verify(inventoryService, never()).findByExternalIdIn(anyList());
  }

  @Test
  void getRecommendationResults_shouldThrowExceptionWhenApiResponseIsNull() {
    // Given
    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(null);

    // When / Then
    assertThatThrownBy(
            () ->
                recommendationService.getRecommendationResults(
                    "campaign123", "run123", 0, 20, null, null, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to get recommendation results for runId: run123");
  }

  @Test
  void getRecommendationResults_shouldThrowExceptionWhenApiResponseIsNotSuccess() {
    // Given
    apiResponse.setSuccess(false);
    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);

    // When / Then
    assertThatThrownBy(
            () ->
                recommendationService.getRecommendationResults(
                    "campaign123", "run123", 0, 20, null, null, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to get recommendation results for runId: run123");
  }

  @Test
  void getRecommendationResults_shouldThrowExceptionWhenCampaignNotFound() {
    // Given
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();
    recommendations.add(createRecommendation("inv1", "ext1", "UNSELECTED"));
    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(
            () ->
                recommendationService.getRecommendationResults(
                    "campaign123", "run123", 0, 20, null, null, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Campaign not found: campaign123");
  }

  @Test
  void getRecommendationResults_shouldHandleNullInventoryIdInRecommendation() {
    // Given
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();

    PaginatedRecommendationResponseDTO.RecommendedInventory recWithNullId =
        new PaginatedRecommendationResponseDTO.RecommendedInventory();
    recWithNullId.setInventoryId(null);
    recWithNullId.setSelectionMode("UNSELECTED");
    recommendations.add(recWithNullId);

    PaginatedRecommendationResponseDTO.RecommendedInventory validRec =
        createRecommendation("inv1", "ext1", "UNSELECTED");
    recommendations.add(validRec);

    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    Inventory inventory1 = new Inventory();
    inventory1.setId("internal1");
    inventory1.setExternalId("ext1");

    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory1));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then - Should handle null inventoryId gracefully
    assertThat(result).isNotNull();
    assertThat(result.getRecommendations()).isNotEmpty();
  }

  @Test
  void getRecommendationResults_shouldReturnEmptyListWhenAllRecommendationsFiltered() {
    // Given
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();

    // All recommendations have null internal IDs
    recommendations.add(createRecommendation("inv1", "ext1", "UNSELECTED"));
    recommendations.add(createRecommendation("inv2", "ext2", "UNSELECTED"));

    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    // No inventories found in DB
    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(Collections.emptyList());

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getRecommendations()).isEmpty();
  }

  @Test
  void getRecommendationResults_shouldPopulateDigitalFieldsFromInventory() {
    // Given
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();
    recommendations.add(createRecommendation("inv1", "ext1", "UNSELECTED"));
    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    Inventory inventory = new Inventory();
    inventory.setId("internal1");
    inventory.setExternalId("ext1");
    inventory.setSize("48x14");
    inventory.setInventoryCluster(List.of("cluster-A"));
    Inventory.DigitalFields df = new Inventory.DigitalFields();
    df.setSpotDuration(10);
    df.setSpotsPerLoop(2);
    df.setBookingMode("loop");
    df.setPlayerCount(1);
    inventory.setDigitalFields(df);

    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result.getRecommendations()).hasSize(1);
    PaginatedRecommendationResponseDTO.InventoryDetails details =
        result.getRecommendations().get(0).getInventoryDetails();
    assertThat(details.getSize()).isEqualTo("48x14");
    assertThat(details.getInventoryCluster()).containsExactly("cluster-A");
    InventoryResponseDTO.DigitalFieldsDTO digitalFields = details.getDigitalFields();
    assertThat(digitalFields).isNotNull();
    assertThat(digitalFields.getSpotDuration()).isEqualTo(10);
    assertThat(digitalFields.getSpotsPerLoop()).isEqualTo(2);
    assertThat(digitalFields.getBookingMode()).isEqualTo("loop");
    assertThat(digitalFields.getPlayerCount()).isEqualTo(1);
  }

  @Test
  void getRecommendationResults_shouldNotSetDigitalFieldsWhenInventoryHasNoDigitalFields() {
    // Given
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();
    recommendations.add(createRecommendation("inv1", "ext1", "UNSELECTED"));
    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    Inventory inventory = new Inventory();
    inventory.setId("internal1");
    inventory.setExternalId("ext1");
    // no digitalFields, size, or inventoryCluster set

    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result.getRecommendations()).hasSize(1);
    PaginatedRecommendationResponseDTO.InventoryDetails details =
        result.getRecommendations().get(0).getInventoryDetails();
    assertThat(details.getDigitalFields()).isNull();
    assertThat(details.getSize()).isNull();
    assertThat(details.getInventoryCluster()).isNull();
  }

  @Test
  void getRecommendationResults_shouldIncludeSpotRateInPerformance() {
    // Given: recommendation with spotRate in inventoryDetails
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();

    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        createRecommendation("inv1", "ext1", "UNSELECTED");
    rec.getInventoryDetails().setSpotRate(3.5);
    rec.getInventoryDetails().setCpmRate(12.0);

    PaginatedRecommendationResponseDTO.AvailabilitySummary availability =
        new PaginatedRecommendationResponseDTO.AvailabilitySummary();
    availability.setTotalDays(30);
    rec.setAvailability(availability);

    PaginatedRecommendationResponseDTO.CostEstimate costEstimate =
        new PaginatedRecommendationResponseDTO.CostEstimate();
    costEstimate.setTotalAdPlays(10000L);
    rec.setCost(costEstimate);

    recommendations.add(rec);
    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    Inventory inventory = new Inventory();
    inventory.setId("internal1");
    inventory.setExternalId("ext1");
    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result.getRecommendations()).hasSize(1);
    PaginatedRecommendationResponseDTO.Performance performance =
        result.getRecommendations().get(0).getPerformance();
    assertThat(performance).isNotNull();
    assertThat(performance.getSpotRate()).isEqualTo(3.5);
    assertThat(performance.getCpmRate()).isEqualTo(12.0);
  }

  @Test
  void getRecommendationResults_shouldHandleNullSpotRateInPerformance() {
    // Given: recommendation without spotRate in inventoryDetails
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();

    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        createRecommendation("inv1", "ext1", "UNSELECTED");
    rec.getInventoryDetails().setSpotRate(null);
    rec.getInventoryDetails().setCpmRate(null);

    PaginatedRecommendationResponseDTO.AvailabilitySummary availability =
        new PaginatedRecommendationResponseDTO.AvailabilitySummary();
    availability.setTotalDays(30);
    rec.setAvailability(availability);

    PaginatedRecommendationResponseDTO.CostEstimate costEstimate =
        new PaginatedRecommendationResponseDTO.CostEstimate();
    costEstimate.setTotalAdPlays(10000L);
    rec.setCost(costEstimate);

    recommendations.add(rec);
    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    Inventory inventory = new Inventory();
    inventory.setId("internal1");
    inventory.setExternalId("ext1");
    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then
    assertThat(result.getRecommendations()).hasSize(1);
    PaginatedRecommendationResponseDTO.Performance performance =
        result.getRecommendations().get(0).getPerformance();
    assertThat(performance).isNotNull();
    assertThat(performance.getSpotRate()).isNull();
    assertThat(performance.getCpmRate()).isNull();
  }

  @Test
  void getRecommendationResults_shouldHandleNullInventoryDetailsForSpotRate() {
    // Given: recommendation with null inventoryDetails
    List<PaginatedRecommendationResponseDTO.RecommendedInventory> recommendations =
        new ArrayList<>();

    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        new PaginatedRecommendationResponseDTO.RecommendedInventory();
    rec.setInventoryId("ext1");
    rec.setSelectionMode("UNSELECTED");
    rec.setInventoryDetails(null);

    PaginatedRecommendationResponseDTO.AvailabilitySummary availability =
        new PaginatedRecommendationResponseDTO.AvailabilitySummary();
    availability.setTotalDays(30);
    rec.setAvailability(availability);

    PaginatedRecommendationResponseDTO.CostEstimate costEstimate =
        new PaginatedRecommendationResponseDTO.CostEstimate();
    costEstimate.setTotalAdPlays(10000L);
    rec.setCost(costEstimate);

    recommendations.add(rec);
    responseDTO.setRecommendations(recommendations);

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    Inventory inventory = new Inventory();
    inventory.setId("internal1");
    inventory.setExternalId("ext1");
    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory));

    // When
    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Then: Should filter out recommendations with null inventoryDetails
    assertThat(result.getRecommendations()).isEmpty();
  }

  @Test
  void generateRecommendation_withMediaOwnerIds_shouldSetThemOnEngineRequest() {
    // Given
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    statusApiResponse.setData(statusResponse);

    ArgumentCaptor<RecommendationRequestDTO> requestCaptor =
        ArgumentCaptor.forClass(RecommendationRequestDTO.class);
    when(apiClient.generateRecommendation(eq("campaign123"), requestCaptor.capture(), eq(false)))
        .thenReturn(statusApiResponse);

    GenerateRecommendationRequestDTO generateRequest =
        GenerateRecommendationRequestDTO.builder()
            .mediaOwnerIds(Arrays.asList("mo1", "mo2"))
            .build();

    // When
    RecommendationStatusResponseDTO result =
        recommendationService.generateRecommendation("campaign123", generateRequest, false);

    // Then
    assertThat(result).isNotNull();
    assertThat(requestCaptor.getValue().getMediaOwnerIds()).containsExactly("mo1", "mo2");
  }

  @Test
  void generateRecommendation_nullBody_shouldNotSetMediaOwnerIds() {
    // Given
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    statusApiResponse.setData(statusResponse);

    ArgumentCaptor<RecommendationRequestDTO> requestCaptor =
        ArgumentCaptor.forClass(RecommendationRequestDTO.class);
    when(apiClient.generateRecommendation(eq("campaign123"), requestCaptor.capture(), eq(false)))
        .thenReturn(statusApiResponse);

    // When
    RecommendationStatusResponseDTO result =
        recommendationService.generateRecommendation("campaign123", null, false);

    // Then
    assertThat(result).isNotNull();
    assertThat(requestCaptor.getValue().getMediaOwnerIds()).isNull();
  }

  @Test
  void generateRecommendation_emptyMediaOwnerIds_shouldNotSetThem() {
    // Given
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    statusApiResponse.setData(statusResponse);

    ArgumentCaptor<RecommendationRequestDTO> requestCaptor =
        ArgumentCaptor.forClass(RecommendationRequestDTO.class);
    when(apiClient.generateRecommendation(eq("campaign123"), requestCaptor.capture(), eq(false)))
        .thenReturn(statusApiResponse);

    GenerateRecommendationRequestDTO generateRequest =
        GenerateRecommendationRequestDTO.builder().mediaOwnerIds(Collections.emptyList()).build();

    // When
    RecommendationStatusResponseDTO result =
        recommendationService.generateRecommendation("campaign123", generateRequest, false);

    // Then
    assertThat(result).isNotNull();
    assertThat(requestCaptor.getValue().getMediaOwnerIds()).isNull();
  }

  @Test
  void generateRecommendation_forceRegenerateTrue_shouldForwardTrueToApiClient() {
    // Given
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    statusApiResponse.setData(statusResponse);

    when(apiClient.generateRecommendation(
            eq("campaign123"), any(RecommendationRequestDTO.class), eq(true)))
        .thenReturn(statusApiResponse);

    // When
    RecommendationStatusResponseDTO result =
        recommendationService.generateRecommendation("campaign123", null, true);

    // Then
    assertThat(result).isNotNull();
    verify(apiClient)
        .generateRecommendation(eq("campaign123"), any(RecommendationRequestDTO.class), eq(true));
  }

  @Test
  void generateRecommendation_forceRegenerateFalse_shouldForwardFalseToApiClient() {
    // Given
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    statusApiResponse.setData(statusResponse);

    when(apiClient.generateRecommendation(
            eq("campaign123"), any(RecommendationRequestDTO.class), eq(false)))
        .thenReturn(statusApiResponse);

    // When
    RecommendationStatusResponseDTO result =
        recommendationService.generateRecommendation("campaign123", null, false);

    // Then
    assertThat(result).isNotNull();
    verify(apiClient)
        .generateRecommendation(eq("campaign123"), any(RecommendationRequestDTO.class), eq(false));
  }

  // Helper method to create recommendation with inventory details
  private PaginatedRecommendationResponseDTO.RecommendedInventory createRecommendation(
      String name, String externalId, String selectionMode) {
    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        new PaginatedRecommendationResponseDTO.RecommendedInventory();
    rec.setInventoryId(externalId);
    rec.setName(name);
    rec.setSelectionMode(selectionMode);

    PaginatedRecommendationResponseDTO.InventoryDetails details =
        new PaginatedRecommendationResponseDTO.InventoryDetails();
    details.setName(name);
    rec.setInventoryDetails(details);

    return rec;
  }

  // ─── searchKeywords / buildRecommendationRequest ──────────────────────────

  @Test
  void buildRecommendationRequest_nonCircleLocations_goToSearchKeywords_noGeofences() {
    Campaign campaign =
        campaignWithLocations(
            location("Bengaluru", true, metadata("City")),
            location(
                "BGS Global Institute of Medical Sciences",
                true,
                metadata("Educational Institution")));

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords())
        .containsExactly("Bengaluru", "BGS Global Institute of Medical Sciences");
    assertThat(request.getGeographyTargeting()).isNull();
  }

  @Test
  void buildRecommendationRequest_circleLocation_buildsGeofence_noSearchKeywords() {
    Campaign campaign = campaignWithLocations(location("Some Circle", true, metadata("circle")));

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).isNull();
    assertThat(request.getGeographyTargeting()).isNotNull();
    assertThat(request.getGeographyTargeting().getGeofences()).hasSize(1);
    assertThat(request.getGeographyTargeting().getGeofences().get(0).getType()).isEqualTo("Circle");
  }

  @Test
  void buildRecommendationRequest_circleLocation_typeIsCaseInsensitive() {
    for (String circleType : List.of("Circle", "CIRCLE")) {
      Campaign campaign =
          campaignWithLocations(location("Some Circle", true, metadata(circleType)));

      RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

      assertThat(request.getSearchKeywords()).as("type=%s", circleType).isNull();
      assertThat(request.getGeographyTargeting().getGeofences())
          .as("type=%s", circleType)
          .hasSize(1);
    }
  }

  @Test
  void buildRecommendationRequest_nullMetadataLocation_goesToSearchKeywords() {
    Campaign campaign = campaignWithLocations(location("Mysuru", true, null));

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).containsExactly("Mysuru");
    assertThat(request.getGeographyTargeting()).isNull();
  }

  @Test
  void buildRecommendationRequest_excludedLocations_areIgnored() {
    Campaign campaign =
        campaignWithLocations(
            location("Excluded City", false, metadata("City")),
            location("Excluded Circle", false, metadata("circle")));

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).isNull();
    assertThat(request.getGeographyTargeting()).isNull();
  }

  @Test
  void buildRecommendationRequest_blankOrNullNameNonCircle_areSkipped() {
    Campaign campaign =
        campaignWithLocations(
            location(null, true, metadata("City")),
            location("   ", true, metadata("City")),
            location("Bengaluru", true, metadata("City")));

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).containsExactly("Bengaluru");
    assertThat(request.getGeographyTargeting()).isNull();
  }

  @Test
  void buildRecommendationRequest_mixedCircleAndNonCircle_areSplit() {
    Campaign campaign =
        campaignWithLocations(
            location("Bengaluru", true, metadata("City")),
            location("Circle A", true, metadata("circle")),
            location("Mysuru", true, null));

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).containsExactly("Bengaluru", "Mysuru");
    assertThat(request.getGeographyTargeting().getGeofences()).hasSize(1);
    assertThat(request.getGeographyTargeting().getGeofences().get(0).getType()).isEqualTo("Circle");
  }

  @Test
  void buildRecommendationRequest_noLocations_searchKeywordsNull() {
    Campaign campaign = campaignWithLocations(/* none */ );

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).isNull();
    assertThat(request.getGeographyTargeting()).isNull();
  }

  @Test
  void buildRecommendationRequest_nullGeofencing_searchKeywordsNull() {
    Campaign campaign =
        baseCampaignBuilder().targeting(Campaign.Targeting.builder().build()).build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).isNull();
    assertThat(request.getGeographyTargeting()).isNull();
  }

  @Test
  void buildRecommendationRequest_nullTargeting_searchKeywordsNull() {
    Campaign campaign = baseCampaignBuilder().build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getSearchKeywords()).isNull();
    assertThat(request.getGeographyTargeting()).isNull();
  }

  // Campaign has several @NonNull fields; this populates them so builds don't NPE.
  private Campaign.CampaignBuilder baseCampaignBuilder() {
    return Campaign.builder()
        .name("Test Campaign")
        .startDate(LocalDate.now().plusDays(1))
        .endDate(LocalDate.now().plusDays(30))
        .userId("user123")
        .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
        .companyId("company123");
  }

  private Campaign campaignWithLocations(Campaign.Targeting.Geofencing.Location... locations) {
    Campaign.Targeting.Geofencing geofencing =
        Campaign.Targeting.Geofencing.builder().locations(Arrays.asList(locations)).build();
    return baseCampaignBuilder()
        .targeting(Campaign.Targeting.builder().geofencing(geofencing).build())
        .build();
  }

  private Campaign.Targeting.Geofencing.Location location(
      String name, boolean included, Map<String, String> metadata) {
    // lat/lng are @NonNull on Location.builder() — supply dummy coordinates for every location.
    return Campaign.Targeting.Geofencing.Location.builder()
        .name(name)
        .lat(0.0)
        .lng(0.0)
        .isIncluded(included)
        .metadata(metadata)
        .build();
  }

  private Map<String, String> metadata(String type) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("type", type);
    return metadata;
  }

  @Test
  void buildRecommendationRequest_WithDigitalAndClassicVenueTypes_SetsVenueTypeIds() {
    Campaign.Targeting.VenueTypes venueTypes =
        Campaign.Targeting.VenueTypes.builder()
            .digitalOoh(Arrays.asList("health-beauty-gyms", "transit-airports"))
            .classicOoh(Arrays.asList("outdoor-billboards"))
            .build();
    Campaign.Targeting targeting = Campaign.Targeting.builder().venueTypes(venueTypes).build();
    Campaign campaign =
        Campaign.builder()
            .name("Venue Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .targeting(targeting)
            .build();

    Map<String, String> slugToId = new HashMap<>();
    slugToId.put("health-beauty-gyms", "401");
    slugToId.put("transit-airports", "305");
    slugToId.put("outdoor-billboards", "301");
    when(venuesService.getVenueSlugToIdMap()).thenReturn(slugToId);

    RecommendationRequestDTO result = recommendationService.buildRecommendationRequest(campaign);

    assertThat(result.getAudienceTargeting()).isNotNull();
    RecommendationRequestDTO.VenueTypeIds venueTypeIds =
        result.getAudienceTargeting().getVenueTypeIds();
    assertThat(venueTypeIds).isNotNull();
    assertThat(venueTypeIds.getDigital()).containsExactly("401", "305");
    assertThat(venueTypeIds.getClassic()).containsExactly("301");
  }

  @Test
  void buildRecommendationRequest_WithVenueTypesNotInMap_VenueTypeIdsIsNull() {
    Campaign.Targeting.VenueTypes venueTypes =
        Campaign.Targeting.VenueTypes.builder()
            .digitalOoh(Arrays.asList("unknown-slug"))
            .classicOoh(null)
            .build();
    Campaign.Targeting targeting = Campaign.Targeting.builder().venueTypes(venueTypes).build();
    Campaign campaign =
        Campaign.builder()
            .name("Venue Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .targeting(targeting)
            .build();

    when(venuesService.getVenueSlugToIdMap()).thenReturn(new HashMap<>());

    RecommendationRequestDTO result = recommendationService.buildRecommendationRequest(campaign);

    assertThat(result.getAudienceTargeting().getVenueTypeIds()).isNull();
  }

  @Test
  void buildRecommendationRequest_WithNoVenueTypes_VenueTypeIdsIsNull() {
    Campaign.Targeting targeting = Campaign.Targeting.builder().venueTypes(null).build();
    Campaign campaign =
        Campaign.builder()
            .name("Venue Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .targeting(targeting)
            .build();

    RecommendationRequestDTO result = recommendationService.buildRecommendationRequest(campaign);

    assertThat(result.getAudienceTargeting().getVenueTypeIds()).isNull();
  }

  // ─── mapGoalType / goals mapping ─────────────────────────────────────────

  private Campaign campaignWithGoal(Campaign.Goals.GoalType goalType, Double targetValue) {
    Campaign.Goals goals = new Campaign.Goals();
    goals.setGoalType(goalType);
    goals.setTargetValue(targetValue);
    return baseCampaignBuilder().goals(goals).build();
  }

  @Test
  void buildRecommendationRequest_mapsGoalTypesToCampaignGoals() {
    assertThat(
            recommendationService
                .buildRecommendationRequest(
                    campaignWithGoal(Campaign.Goals.GoalType.IMPRESSIONS, 100.0))
                .getGoal())
        .isEqualTo(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    assertThat(
            recommendationService
                .buildRecommendationRequest(campaignWithGoal(Campaign.Goals.GoalType.REACH, 100.0))
                .getGoal())
        .isEqualTo(RecommendationRequestDTO.CampaignGoal.REACH);
    assertThat(
            recommendationService
                .buildRecommendationRequest(campaignWithGoal(Campaign.Goals.GoalType.SOV, 100.0))
                .getGoal())
        .isEqualTo(RecommendationRequestDTO.CampaignGoal.SOV);
    assertThat(
            recommendationService
                .buildRecommendationRequest(
                    campaignWithGoal(Campaign.Goals.GoalType.ADPLAYS, 100.0))
                .getGoal())
        .isEqualTo(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);
    assertThat(
            recommendationService
                .buildRecommendationRequest(campaignWithGoal(Campaign.Goals.GoalType.OTHER, 100.0))
                .getGoal())
        .isNull();
    assertThat(
            recommendationService
                .buildRecommendationRequest(
                    campaignWithGoal(Campaign.Goals.GoalType.ATTRIBUTION, 100.0))
                .getGoal())
        .isNull();
  }

  @Test
  void buildRecommendationRequest_withGoalTargetValue_setsGoalValue() {
    RecommendationRequestDTO request =
        recommendationService.buildRecommendationRequest(
            campaignWithGoal(Campaign.Goals.GoalType.IMPRESSIONS, 12345.0));

    assertThat(request.getGoalValue()).isEqualTo(12345L);
  }

  @Test
  void buildRecommendationRequest_withNullTargetValue_leavesGoalValueNull() {
    RecommendationRequestDTO request =
        recommendationService.buildRecommendationRequest(
            campaignWithGoal(Campaign.Goals.GoalType.IMPRESSIONS, null));

    assertThat(request.getGoalValue()).isNull();
  }

  @Test
  void buildRecommendationRequest_withBudgetAndBrand_populatesRequest() {
    Campaign campaign =
        baseCampaignBuilder()
            .budget(5000.0)
            .brand(Campaign.CampaignBrand.builder().id("brand9").build())
            .budgetAllocation(Map.of("inv1", 1.0))
            .build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getBudget()).isEqualByComparingTo(BigDecimal.valueOf(5000.0));
    assertThat(request.getBrandId()).isEqualTo("brand9");
    assertThat(request.getBudgetAllocation()).containsEntry("inv1", 1.0);
  }

  // ─── DSP / programmaticEnabled ────────────────────────────────────────────

  @Test
  void buildRecommendationRequest_withDsp_setsDspsAndProgrammaticEnabled() {
    Campaign campaign = baseCampaignBuilder().dsp("XANDR").build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getDsps()).containsExactly("XANDR");
    assertThat(request.getProgrammaticEnabled()).isTrue();
  }

  @Test
  void buildRecommendationRequest_withActivateDsp_expandsToActivateAndMax() {
    Campaign campaign = baseCampaignBuilder().dsp("ACTIVATE").build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getDsps()).containsExactly("ACTIVATE", "MAX");
    assertThat(request.getProgrammaticEnabled()).isTrue();
  }

  @Test
  void buildRecommendationRequest_withLowercaseDsp_isForcedUppercase() {
    RecommendationRequestDTO activate =
        recommendationService.buildRecommendationRequest(
            baseCampaignBuilder().dsp("  activate ").build());
    assertThat(activate.getDsps()).containsExactly("ACTIVATE", "MAX");

    RecommendationRequestDTO xandr =
        recommendationService.buildRecommendationRequest(
            baseCampaignBuilder().dsp("xandr").build());
    assertThat(xandr.getDsps()).containsExactly("XANDR");
  }

  @Test
  void buildRecommendationRequest_withNullDsp_leavesDspsAndProgrammaticEnabledNull() {
    Campaign campaign = baseCampaignBuilder().build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getDsps()).isNull();
    assertThat(request.getProgrammaticEnabled()).isNull();
  }

  @Test
  void buildRecommendationRequest_withBlankDsp_leavesDspsAndProgrammaticEnabledNull() {
    Campaign campaign = baseCampaignBuilder().dsp("   ").build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getDsps()).isNull();
    assertThat(request.getProgrammaticEnabled()).isNull();
  }

  @Test
  void buildRecommendationRequest_withProgrammaticOnlyTrueAndNoDsp_setsProgrammaticEnabledTrue() {
    Campaign campaign =
        baseCampaignBuilder()
            .targeting(Campaign.Targeting.builder().programmaticOnly(true).build())
            .build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getProgrammaticEnabled()).isTrue();
    assertThat(request.getDsps()).isNull(); // programmaticOnly does not populate dsps
  }

  @Test
  void
      buildRecommendationRequest_withProgrammaticOnlyFalseAndNoDsp_leavesProgrammaticEnabledNull() {
    Campaign campaign =
        baseCampaignBuilder()
            .targeting(Campaign.Targeting.builder().programmaticOnly(false).build())
            .build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getProgrammaticEnabled()).isNull(); // never forced to false
  }

  @Test
  void buildRecommendationRequest_withProgrammaticOnlyAndDsp_keepsBothDspsAndProgrammaticEnabled() {
    Campaign campaign =
        baseCampaignBuilder()
            .dsp("XANDR")
            .targeting(Campaign.Targeting.builder().programmaticOnly(true).build())
            .build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getDsps()).containsExactly("XANDR");
    assertThat(request.getProgrammaticEnabled()).isTrue();
  }

  // ─── buildRecommendationRequest / inventoryCluster ────────────────────────

  @Test
  void buildRecommendationRequest_withInventoryCluster_setsInventoryCluster() {
    Campaign campaign =
        baseCampaignBuilder()
            .targeting(
                Campaign.Targeting.builder()
                    .inventoryCluster(List.of("cluster-A", "cluster-B"))
                    .build())
            .build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getInventoryCluster()).containsExactly("cluster-A", "cluster-B");
  }

  @Test
  void buildRecommendationRequest_withNullInventoryCluster_leavesInventoryClusterNull() {
    Campaign campaign =
        baseCampaignBuilder().targeting(Campaign.Targeting.builder().build()).build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getInventoryCluster()).isNull();
  }

  @Test
  void buildRecommendationRequest_withEmptyInventoryClusterList_leavesInventoryClusterNull() {
    Campaign campaign =
        baseCampaignBuilder()
            .targeting(Campaign.Targeting.builder().inventoryCluster(List.of()).build())
            .build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getInventoryCluster()).isNull();
  }

  @Test
  void buildRecommendationRequest_withNullTargeting_leavesInventoryClusterNull() {
    Campaign campaign = baseCampaignBuilder().build();

    RecommendationRequestDTO request = recommendationService.buildRecommendationRequest(campaign);

    assertThat(request.getInventoryCluster()).isNull();
  }

  // ─── buildAudienceTargeting / convert* / toCanonicalIncome ────────────────

  @Test
  void buildRecommendationRequest_convertsDemographicsAndMergesSegments() {
    Map<String, List<String>> demographics = new HashMap<>();
    demographics.put("age", List.of("18_24", "25_34"));
    demographics.put("gender", List.of("male", "female"));
    demographics.put(
        "income", List.of("low", "lower_middle", "middle", "upper_middle", "high", "weird", ""));
    demographics.put("interests", List.of("sports"));
    demographics.put("behavior", List.of("segmentA"));
    Campaign.Targeting targeting =
        Campaign.Targeting.builder().demographics(demographics).signals(List.of("signalX")).build();
    Campaign campaign = baseCampaignBuilder().targeting(targeting).build();

    RecommendationRequestDTO.AudienceTargeting audience =
        recommendationService.buildRecommendationRequest(campaign).getAudienceTargeting();

    assertThat(audience.getDemographics().get("age")).containsExactly("18-24", "25-34");
    assertThat(audience.getDemographics().get("gender")).containsExactly("MALE", "FEMALE");
    assertThat(audience.getDemographics().get("income"))
        .containsExactly("Low", "Lower-middle", "Middle", "Upper-middle", "High", "Weird", "");
    assertThat(audience.getDemographics().get("interests")).containsExactly("sports");
    assertThat(audience.getAudienceSegments()).containsExactly("segmentA", "signalX");
  }

  @Test
  void buildRecommendationRequest_withEmptyDemographicsAndSignals_leavesAudienceNull() {
    Campaign.Targeting targeting =
        Campaign.Targeting.builder().demographics(new HashMap<>()).build();
    Campaign campaign = baseCampaignBuilder().targeting(targeting).build();

    RecommendationRequestDTO.AudienceTargeting audience =
        recommendationService.buildRecommendationRequest(campaign).getAudienceTargeting();

    assertThat(audience.getDemographics()).isNull();
    assertThat(audience.getAudienceSegments()).isNull();
  }

  // ─── buildGeographyTargeting geometries ───────────────────────────────────

  @Test
  void buildRecommendationRequest_includedGeometry_buildsPolygonGeofence_excludedIgnored() {
    Campaign.Targeting.Geofencing.Geometry included =
        Campaign.Targeting.Geofencing.Geometry.builder()
            .name("zone-in")
            .type("Polygon")
            .coordinates(List.of(List.of(1.0, 2.0)))
            .isIncluded(true)
            .build();
    Campaign.Targeting.Geofencing.Geometry excluded =
        Campaign.Targeting.Geofencing.Geometry.builder()
            .name("zone-out")
            .type("Polygon")
            .coordinates(List.of(List.of(3.0, 4.0)))
            .isIncluded(false)
            .build();
    Campaign.Targeting.Geofencing geofencing =
        Campaign.Targeting.Geofencing.builder().geometries(List.of(included, excluded)).build();
    Campaign campaign =
        baseCampaignBuilder()
            .targeting(Campaign.Targeting.builder().geofencing(geofencing).build())
            .build();

    RecommendationRequestDTO.GeographyTargeting geo =
        recommendationService.buildRecommendationRequest(campaign).getGeographyTargeting();

    assertThat(geo).isNotNull();
    assertThat(geo.getGeofences()).hasSize(1);
    assertThat(geo.getGeofences().get(0).getType()).isEqualTo("Polygon");
  }

  // ─── updateCampaignCompanyAccess ──────────────────────────────────────────

  @Test
  void updateCampaignCompanyAccess_whenAccessNull_initializesAndAddsAndEvicts() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setId("campaign123");
    campaign.setCompanyAccess(null);

    recommendationService.updateCampaignCompanyAccess(campaign, "mo1");

    assertThat(campaign.getCompanyAccess()).containsExactly("mo1");
    verify(campaignService).save(campaign);
    verify(campaignService).campaignCacheEvict("campaign123");
  }

  @Test
  void updateCampaignCompanyAccess_whenAlreadyPresent_doesNotSaveOrEvict() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setId("campaign123");
    campaign.setCompanyAccess(new ArrayList<>(List.of("mo1")));

    recommendationService.updateCampaignCompanyAccess(campaign, "mo1");

    verify(campaignService, never()).save(any());
    verify(campaignService, never()).campaignCacheEvict(anyString());
  }

  // ─── generateRecommendation error/branch paths ────────────────────────────

  @Test
  void generateRecommendation_whenCampaignNotFound_throws() {
    when(campaignRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> recommendationService.generateRecommendation("missing", null, false))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Campaign not found: missing");
  }

  @Test
  void generateRecommendation_whenApiResponseNull_throws() {
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    when(apiClient.generateRecommendation(eq("campaign123"), any(), eq(false))).thenReturn(null);

    assertThatThrownBy(
            () -> recommendationService.generateRecommendation("campaign123", null, false))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to generate recommendation");
  }

  @Test
  void generateRecommendation_whenCompletedNewRunAndSkipRecommendation_doesNotSync() {
    testCampaign.setRunId("oldRun");
    testCampaign.setSkipRecommendation(Boolean.TRUE);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("newRun");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.COMPLETED);
    statusApiResponse.setData(statusResponse);
    when(apiClient.generateRecommendation(eq("campaign123"), any(), eq(false)))
        .thenReturn(statusApiResponse);

    recommendationService.generateRecommendation("campaign123", null, false);

    verify(inventorySchedulesRepository, never()).deleteByCampaignId(anyString());
  }

  @Test
  void generateRecommendation_whenCompletedNewRun_handlesRunAndAutoSelects() {
    testCampaign.setRunId("oldRun");
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("newRun");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.COMPLETED);
    RecommendationStatusResponseDTO.RecommendationMetadata metadata =
        RecommendationStatusResponseDTO.RecommendationMetadata.builder()
            .autoSelectedInventoryIds(List.of("ext1"))
            .build();
    statusResponse.setMetadata(metadata);
    statusApiResponse.setData(statusResponse);
    when(apiClient.generateRecommendation(eq("campaign123"), any(), eq(false)))
        .thenReturn(statusApiResponse);

    // handleNewCompletedRun: existing CIS to delete
    CampaignInventorySchedules existingCis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo0")
            .inventoryId("internal0")
            .scheduleIds(List.of("sOld"))
            .build();
    when(inventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(List.of(existingCis));

    // fetchAndStoreSchedules: recommended schedules for ext1
    RunSchedulesResponseDTO.RunScheduleItemDTO item =
        RunSchedulesResponseDTO.RunScheduleItemDTO.builder()
            .inventoryId("ext1")
            .scheduleStartDate(LocalDate.of(2024, 1, 15))
            .scheduleEndDate(LocalDate.of(2024, 1, 20))
            .bookingMatrix(Map.of("2024-01-15", List.of(1), "bad-key", List.of(2)))
            .build();
    RunSchedulesResponseDTO schedulesData =
        RunSchedulesResponseDTO.builder().runId("newRun").schedules(List.of(item)).build();
    RecommendationApiResponse<RunSchedulesResponseDTO> schedulesApiResponse =
        new RecommendationApiResponse<>();
    schedulesApiResponse.setSuccess(true);
    schedulesApiResponse.setData(schedulesData);
    when(apiClient.getRecommendedSchedules("newRun")).thenReturn(schedulesApiResponse);

    Inventory inv = new Inventory();
    inv.setId("internal1");
    inv.setExternalId("ext1");
    inv.setMediaOwnerId("mo1");
    when(inventoryService.findByExternalIdIn(List.of("ext1"))).thenReturn(List.of(inv));
    when(scheduleRepository.save(any(Schedule.class)))
        .thenAnswer(
            invocation -> {
              Schedule s = invocation.getArgument(0);
              s.setId("sch1");
              return s;
            });

    RecommendationStatusResponseDTO result =
        recommendationService.generateRecommendation("campaign123", null, false);

    assertThat(result.getRunId()).isEqualTo("newRun");
    verify(scheduleRepository).deleteByIdIn(List.of("sOld"));
    verify(inventorySchedulesRepository).deleteByCampaignId("campaign123");
    verify(scheduleRepository).save(any(Schedule.class));
    ArgumentCaptor<CampaignInventorySchedules> cisCaptor =
        ArgumentCaptor.forClass(CampaignInventorySchedules.class);
    verify(inventorySchedulesRepository).save(cisCaptor.capture());
    assertThat(cisCaptor.getValue().getScheduleIds()).containsExactly("sch1");
  }

  @Test
  void
      generateRecommendation_whenCompletedButSchedulesResponseUnsuccessful_skipsScheduleCreation() {
    testCampaign.setRunId("oldRun");
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));

    RecommendationApiResponse<RecommendationStatusResponseDTO> statusApiResponse =
        new RecommendationApiResponse<>();
    statusApiResponse.setSuccess(true);
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("newRun");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.COMPLETED);
    statusResponse.setMetadata(
        RecommendationStatusResponseDTO.RecommendationMetadata.builder()
            .autoSelectedInventoryIds(List.of("ext1"))
            .build());
    statusApiResponse.setData(statusResponse);
    when(apiClient.generateRecommendation(eq("campaign123"), any(), eq(false)))
        .thenReturn(statusApiResponse);
    when(inventorySchedulesRepository.findByCampaignId("campaign123"))
        .thenReturn(Collections.emptyList());

    RecommendationApiResponse<RunSchedulesResponseDTO> schedulesApiResponse =
        new RecommendationApiResponse<>();
    schedulesApiResponse.setSuccess(false);
    when(apiClient.getRecommendedSchedules("newRun")).thenReturn(schedulesApiResponse);

    recommendationService.generateRecommendation("campaign123", null, false);

    verify(scheduleRepository, never()).save(any(Schedule.class));
  }

  // ─── syncSelectedInventories ──────────────────────────────────────────────

  @Test
  void syncSelectedInventories_whenCampaignNull_returnsWithoutCallingApi() {
    when(campaignRepository.findById("c1")).thenReturn(Optional.empty());

    recommendationService.syncSelectedInventories(
        "c1", List.of("i1"), RecommendationService.OperationType.SELECT);

    verify(apiClient, never()).manageSelectedInventories(anyString(), anyString(), any());
  }

  @Test
  void syncSelectedInventories_whenRunIdNull_returnsWithoutCallingApi() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setRunId(null);
    when(campaignRepository.findById("c1")).thenReturn(Optional.of(campaign));

    recommendationService.syncSelectedInventories(
        "c1", List.of("i1"), RecommendationService.OperationType.SELECT);

    verify(apiClient, never()).manageSelectedInventories(anyString(), anyString(), any());
  }

  @Test
  void syncSelectedInventories_whenNoExternalIdsResolved_returnsWithoutCallingApi() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setRunId("run1");
    when(campaignRepository.findById("c1")).thenReturn(Optional.of(campaign));
    // inventory resolves but has null externalId -> not added
    Inventory inv = new Inventory();
    inv.setId("i1");
    inv.setExternalId(null);
    when(inventoryService.findAllByIds(List.of("i1"))).thenReturn(List.of(inv));

    recommendationService.syncSelectedInventories(
        "c1", List.of("i1"), RecommendationService.OperationType.SELECT);

    verify(apiClient, never()).manageSelectedInventories(anyString(), anyString(), any());
  }

  @Test
  void syncSelectedInventories_whenInventoryNotFound_isSkippedGracefully() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setRunId("run1");
    when(campaignRepository.findById("c1")).thenReturn(Optional.of(campaign));
    // Batch fetch simply omits IDs it can't find - no exception, no externalId resolved
    when(inventoryService.findAllByIds(List.of("i1"))).thenReturn(List.of());

    recommendationService.syncSelectedInventories(
        "c1", List.of("i1"), RecommendationService.OperationType.SELECT);

    verify(apiClient, never()).manageSelectedInventories(anyString(), anyString(), any());
  }

  @Test
  void syncSelectedInventories_whenResolveThrows_isSkippedGracefully() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setRunId("run1");
    when(campaignRepository.findById("c1")).thenReturn(Optional.of(campaign));
    when(inventoryService.findAllByIds(List.of("i1")))
        .thenThrow(new RuntimeException("db unavailable"));

    recommendationService.syncSelectedInventories(
        "c1", List.of("i1"), RecommendationService.OperationType.SELECT);

    verify(apiClient, never()).manageSelectedInventories(anyString(), anyString(), any());
  }

  @Test
  void syncSelectedInventories_success_callsApiWithExternalIds() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setRunId("run1");
    when(campaignRepository.findById("c1")).thenReturn(Optional.of(campaign));
    Inventory inv = new Inventory();
    inv.setId("i1");
    inv.setExternalId("ext1");
    when(inventoryService.findAllByIds(List.of("i1"))).thenReturn(List.of(inv));

    recommendationService.syncSelectedInventories(
        "c1", List.of("i1"), RecommendationService.OperationType.DESELECT);

    ArgumentCaptor<SelectedInventoriesDTO> dtoCaptor =
        ArgumentCaptor.forClass(SelectedInventoriesDTO.class);
    verify(apiClient).manageSelectedInventories(eq("run1"), eq("DESELECT"), dtoCaptor.capture());
    assertThat(dtoCaptor.getValue().getInventoryIds()).containsExactly("ext1");
  }

  @Test
  void syncSelectedInventories_whenApiThrows_isCaughtGracefully() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setRunId("run1");
    when(campaignRepository.findById("c1")).thenReturn(Optional.of(campaign));
    Inventory inv = new Inventory();
    inv.setId("i1");
    inv.setExternalId("ext1");
    when(inventoryService.findAllByIds(List.of("i1"))).thenReturn(List.of(inv));
    doThrow(new RuntimeException("engine down"))
        .when(apiClient)
        .manageSelectedInventories(anyString(), anyString(), any());

    // Should not throw
    recommendationService.syncSelectedInventories(
        "c1", List.of("i1"), RecommendationService.OperationType.SELECT);

    verify(apiClient).manageSelectedInventories(anyString(), anyString(), any());
  }

  @Test
  @DisplayName(
      "syncSelectedInventories - Should batch-resolve externalIds in a single call, not one getById per inventory")
  void syncSelectedInventories_multipleIds_batchesExternalIdResolution() {
    Campaign campaign = baseCampaignBuilder().build();
    campaign.setRunId("run1");
    when(campaignRepository.findById("c1")).thenReturn(Optional.of(campaign));

    Inventory inv1 = new Inventory();
    inv1.setId("i1");
    inv1.setExternalId("ext1");
    Inventory inv2 = new Inventory();
    inv2.setId("i2");
    inv2.setExternalId("ext2");
    Inventory inv3 = new Inventory();
    inv3.setId("i3");
    inv3.setExternalId(null); // found but no externalId -> skipped
    List<String> requestedIds = List.of("i1", "i2", "i3", "i4"); // i4 not found at all
    when(inventoryService.findAllByIds(requestedIds)).thenReturn(List.of(inv1, inv2, inv3));

    recommendationService.syncSelectedInventories(
        "c1", requestedIds, RecommendationService.OperationType.DESELECT);

    // Single batch call, never one-by-one
    verify(inventoryService, times(1)).findAllByIds(requestedIds);
    verify(inventoryService, never()).getById(anyString());

    ArgumentCaptor<SelectedInventoriesDTO> dtoCaptor =
        ArgumentCaptor.forClass(SelectedInventoriesDTO.class);
    verify(apiClient).manageSelectedInventories(eq("run1"), eq("DESELECT"), dtoCaptor.capture());
    assertThat(dtoCaptor.getValue().getInventoryIds()).containsExactlyInAnyOrder("ext1", "ext2");
  }

  // ─── autoOptimizeSchedulesAndSync ─────────────────────────────────────────

  @Test
  void autoOptimizeSchedulesAndSync_whenCampaignMissing_throws() {
    when(campaignService.existsById("c1")).thenReturn(false);

    assertThatThrownBy(() -> recommendationService.autoOptimizeSchedulesAndSync("c1", "run1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Campaign not found");
  }

  @Test
  void autoOptimizeSchedulesAndSync_whenNoCis_returnsEmptySchedules() {
    when(campaignService.existsById("c1")).thenReturn(true);
    when(inventorySchedulesRepository.findByCampaignId("c1")).thenReturn(Collections.emptyList());

    RunSchedulesResponseDTO result =
        recommendationService.autoOptimizeSchedulesAndSync("c1", "run1");

    assertThat(result.getRunId()).isEqualTo("run1");
    assertThat(result.getSchedules()).isEmpty();
    verify(apiClient, never()).autoOptimizeSchedules(anyString(), any());
  }

  @Test
  void autoOptimizeSchedulesAndSync_whenNoExternalIdsResolved_throws() {
    when(campaignService.existsById("c1")).thenReturn(true);
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .mediaOwnerId("mo1")
            .inventoryId("i1")
            .build();
    when(inventorySchedulesRepository.findByCampaignId("c1")).thenReturn(List.of(cis));
    Inventory inv = new Inventory();
    inv.setId("i1");
    inv.setExternalId(null);
    when(inventoryService.getById("i1")).thenReturn(inv);

    assertThatThrownBy(() -> recommendationService.autoOptimizeSchedulesAndSync("c1", "run1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Could not resolve inventory externalIds");
  }

  @Test
  void autoOptimizeSchedulesAndSync_whenApiFails_throws() {
    when(campaignService.existsById("c1")).thenReturn(true);
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .mediaOwnerId("mo1")
            .inventoryId("i1")
            .build();
    when(inventorySchedulesRepository.findByCampaignId("c1")).thenReturn(List.of(cis));
    Inventory inv = new Inventory();
    inv.setId("i1");
    inv.setExternalId("ext1");
    when(inventoryService.getById("i1")).thenReturn(inv);
    when(apiClient.autoOptimizeSchedules(eq("run1"), any())).thenReturn(null);

    assertThatThrownBy(() -> recommendationService.autoOptimizeSchedulesAndSync("c1", "run1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Auto-optimize schedules failed");
  }

  @Test
  void autoOptimizeSchedulesAndSync_success_recreatesSchedulesAndClearsCis() {
    when(campaignService.existsById("c1")).thenReturn(true);
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .mediaOwnerId("mo1")
            .inventoryId("i1")
            .scheduleIds(new ArrayList<>(List.of("sOld")))
            .build();
    when(inventorySchedulesRepository.findByCampaignId("c1")).thenReturn(List.of(cis));
    Inventory inv = new Inventory();
    inv.setId("i1");
    inv.setExternalId("ext1");
    when(inventoryService.getById("i1")).thenReturn(inv);

    RunSchedulesResponseDTO.RunScheduleItemDTO item =
        RunSchedulesResponseDTO.RunScheduleItemDTO.builder()
            .inventoryId("ext1")
            .scheduleStartDate(LocalDate.of(2024, 1, 15))
            .scheduleEndDate(LocalDate.of(2024, 1, 20))
            .bookingMatrix(Map.of("2024-01-15", List.of(1)))
            .build();
    RunSchedulesResponseDTO data =
        RunSchedulesResponseDTO.builder().runId("run1").schedules(List.of(item)).build();
    RecommendationApiResponse<RunSchedulesResponseDTO> apiResp = new RecommendationApiResponse<>();
    apiResp.setSuccess(true);
    apiResp.setData(data);
    when(apiClient.autoOptimizeSchedules(eq("run1"), any())).thenReturn(apiResp);
    when(scheduleRepository.save(any(Schedule.class)))
        .thenAnswer(
            invocation -> {
              Schedule s = invocation.getArgument(0);
              s.setId("sNew");
              return s;
            });
    when(inventorySchedulesRepository.findByCampaignIdAndInventoryId("c1", "i1"))
        .thenReturn(Optional.of(cis));

    RunSchedulesResponseDTO result =
        recommendationService.autoOptimizeSchedulesAndSync("c1", "run1");

    assertThat(result.getSchedules()).hasSize(1);
    verify(scheduleRepository).deleteByIdIn(List.of("sOld"));
    verify(scheduleRepository).save(any(Schedule.class));
  }

  @Test
  void autoOptimizeSchedulesAndSync_whenNoItemsReturned_returnsDataAfterClearing() {
    when(campaignService.existsById("c1")).thenReturn(true);
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .mediaOwnerId("mo1")
            .inventoryId("i1")
            .build();
    when(inventorySchedulesRepository.findByCampaignId("c1")).thenReturn(List.of(cis));
    Inventory inv = new Inventory();
    inv.setId("i1");
    inv.setExternalId("ext1");
    when(inventoryService.getById("i1")).thenReturn(inv);

    RunSchedulesResponseDTO data =
        RunSchedulesResponseDTO.builder().runId("run1").schedules(Collections.emptyList()).build();
    RecommendationApiResponse<RunSchedulesResponseDTO> apiResp = new RecommendationApiResponse<>();
    apiResp.setSuccess(true);
    apiResp.setData(data);
    when(apiClient.autoOptimizeSchedules(eq("run1"), any())).thenReturn(apiResp);

    RunSchedulesResponseDTO result =
        recommendationService.autoOptimizeSchedulesAndSync("c1", "run1");

    assertThat(result.getSchedules()).isEmpty();
    verify(scheduleRepository, never()).save(any(Schedule.class));
  }

  // ─── buildPerformanceForSelected (AUTO/MANUAL selection modes) ────────────

  @Test
  void getRecommendationResults_selectedWithForecast_buildsPerformanceFromForecast() {
    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        createRecommendation("inv1", "ext1", "AUTO");
    PaginatedRecommendationResponseDTO.AvailabilitySummary availability =
        new PaginatedRecommendationResponseDTO.AvailabilitySummary();
    availability.setTotalDays(10);
    rec.setAvailability(availability);
    responseDTO.setRecommendations(new ArrayList<>(List.of(rec)));

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    Inventory inventory = new Inventory();
    inventory.setId("internal1");
    inventory.setExternalId("ext1");
    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory));

    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign123")
            .mediaOwnerId("mo1")
            .inventoryId("internal1")
            .build();
    when(inventorySchedulesRepository.findByCampaignIdAndInventoryId("campaign123", "internal1"))
        .thenReturn(Optional.of(cis));
    CampaignForecastDTO forecast =
        CampaignForecastDTO.builder()
            .estimatedImpression(1000L)
            .estimatedReach(500L)
            .estimatedAdPlays(2000L)
            .totalCost(300.0)
            .build();
    when(campaignService.calculateCampaignForecast(eq(testCampaign), anyList()))
        .thenReturn(forecast);

    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    PaginatedRecommendationResponseDTO.Performance perf =
        result.getRecommendations().get(0).getPerformance();
    assertThat(perf.getEstimatedImpressions()).isEqualTo(1000L);
    assertThat(perf.getTotalAdPlays()).isEqualTo(2000L);
    assertThat(perf.getPerDayAdPlays()).isEqualTo(200L);
    assertThat(perf.getEstimatedCost()).isEqualTo(300.0);
    assertThat(perf.getPerDayCost()).isEqualTo(30.0);
  }

  @Test
  void getRecommendationResults_selectedButNoCis_fallsBackToUnselectedPerformance() {
    PaginatedRecommendationResponseDTO.RecommendedInventory rec =
        createRecommendation("inv1", "ext1", "MANUAL");
    responseDTO.setRecommendations(new ArrayList<>(List.of(rec)));

    when(apiClient.getRecommendationResults(anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(apiResponse);
    when(campaignRepository.findById("campaign123")).thenReturn(Optional.of(testCampaign));
    Inventory inventory = new Inventory();
    inventory.setId("internal1");
    inventory.setExternalId("ext1");
    when(inventoryService.findByExternalIdIn(anyList())).thenReturn(List.of(inventory));
    when(inventorySchedulesRepository.findByCampaignIdAndInventoryId("campaign123", "internal1"))
        .thenReturn(Optional.empty());

    PaginatedRecommendationResponseDTO result =
        recommendationService.getRecommendationResults(
            "campaign123", "run123", 0, 20, null, null, null);

    // Falls back to unselected performance but the recommendation is retained (internalId set)
    assertThat(result.getRecommendations()).hasSize(1);
    assertThat(result.getRecommendations().get(0).getPerformance()).isNotNull();
    verify(campaignService, never()).calculateCampaignForecast(any(), anyList());
  }
}
