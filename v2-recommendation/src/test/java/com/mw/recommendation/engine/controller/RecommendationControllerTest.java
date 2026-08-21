package com.mw.recommendation.engine.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.*;
import com.mw.recommendation.engine.exception.GlobalExceptionHandler;
import com.mw.recommendation.engine.service.RecommendationService;
import com.mw.recommendation.engine.service.ScheduleRecommendationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

  @Mock private RecommendationService recommendationService;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private MessageSource messageSource;

  @InjectMocks private RecommendationController recommendationController;

  private ObjectMapper objectMapper;
  private MockMvc mockMvc;

  private RecommendationRequestDTO testRequest;
  private RecommendationStatusResponseDTO testStatusResponse;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(recommendationController)
            .setControllerAdvice(new GlobalExceptionHandler(messageSource))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    testRequest = new RecommendationRequestDTO();
    testRequest.setCountry("US");
    testRequest.setStartDate(LocalDate.of(2024, 1, 1));
    testRequest.setEndDate(LocalDate.of(2024, 1, 31));
    testRequest.setProductId("product-1");
    testRequest.setCompanyId("company-1");

    testStatusResponse =
        RecommendationStatusResponseDTO.builder()
            .runId("run-123")
            .status(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS)
            .completionPercentage(0)
            .campaignId("campaign-1")
            .build();
  }

  @Test
  void testSubmitRecommendation_WithValidRequest_ReturnsSuccess() throws Exception {
    when(recommendationService.submitRecommendation(
            anyString(), any(RecommendationRequestDTO.class), anyBoolean()))
        .thenReturn(testStatusResponse);

    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/recommendations", "campaign-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(recommendationService)
        .submitRecommendation(eq("campaign-1"), any(RecommendationRequestDTO.class), eq(false));
  }

  @Test
  void testSubmitRecommendation_ForceRegenerateTrue_PassesTrueToService() throws Exception {
    when(recommendationService.submitRecommendation(
            anyString(), any(RecommendationRequestDTO.class), anyBoolean()))
        .thenReturn(testStatusResponse);

    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/recommendations", "campaign-1")
                .param("forceRegenerate", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(recommendationService)
        .submitRecommendation(eq("campaign-1"), any(RecommendationRequestDTO.class), eq(true));
  }

  @Test
  void testSubmitRecommendation_ForceRegenerateFalse_PassesFalseToService() throws Exception {
    when(recommendationService.submitRecommendation(
            anyString(), any(RecommendationRequestDTO.class), anyBoolean()))
        .thenReturn(testStatusResponse);

    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/recommendations", "campaign-1")
                .param("forceRegenerate", "false")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(recommendationService)
        .submitRecommendation(eq("campaign-1"), any(RecommendationRequestDTO.class), eq(false));
  }

  @Test
  void testSubmitRecommendation_ForceRegenerateEmptyString_BindsToNullAndPassesFalse()
      throws Exception {
    when(recommendationService.submitRecommendation(
            anyString(), any(RecommendationRequestDTO.class), anyBoolean()))
        .thenReturn(testStatusResponse);

    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/recommendations", "campaign-1")
                .param("forceRegenerate", "")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(recommendationService)
        .submitRecommendation(eq("campaign-1"), any(RecommendationRequestDTO.class), eq(false));
  }

  @Test
  void testGetRecommendationResults_WithValidRunId_ReturnsResults() throws Exception {
    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-123")
            .campaignId("campaign-1")
            .recommendations(new ArrayList<>())
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/results", "run-123")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(recommendationService)
        .getRecommendationResults(eq("run-123"), eq(0), eq(20), any(), any(), any());
  }

  @Test
  void testGetRecommendationResults_WithProgrammaticSupportAndDealTypesFilter_ReturnsResults()
      throws Exception {
    RecommendationResultFilterDTO filter = new RecommendationResultFilterDTO();
    filter.setProgrammaticSupport(com.mw.recommendation.engine.enums.ProgrammaticSupport.YES);
    filter.setDealTypes(
        List.of(
            com.mw.recommendation.engine.enums.ProgrammaticDealType.GUARANTEED,
            com.mw.recommendation.engine.enums.ProgrammaticDealType.PREFERRED_DEAL));

    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-123")
            .campaignId("campaign-1")
            .recommendations(new ArrayList<>())
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/results", "run-123")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filter)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(recommendationService)
        .getRecommendationResults(
            eq("run-123"),
            eq(0),
            eq(20),
            any(),
            any(),
            argThat(
                f ->
                    f != null
                        && f.getProgrammaticSupport()
                            == com.mw.recommendation.engine.enums.ProgrammaticSupport.YES
                        && f.getDealTypes() != null
                        && f.getDealTypes().size() == 2));
  }

  @Test
  void testGetRecommendationResults_WithSearchParam_ReturnsResults() throws Exception {
    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-123")
            .campaignId("campaign-1")
            .recommendations(new ArrayList<>())
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/results", "run-123")
                .param("page", "0")
                .param("size", "20")
                .param("search", "foo")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(recommendationService)
        .getRecommendationResults(eq("run-123"), eq(0), eq(20), any(), eq("foo"), any());
  }

  @Test
  void testGetRecommendationResults_includesSelectionMode() throws Exception {
    PaginatedRecommendationResponseDTO.RecommendedInventory recommendation =
        PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
            .inventoryId("inv-1")
            .name("Test Inventory")
            .finalScore(85.0)
            .selectionMode("AUTO")
            .build();
    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-123")
            .campaignId("campaign-1")
            .recommendations(List.of(recommendation))
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/results", "run-123")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recommendations[0].selectionMode").value("AUTO"));
  }

  // ---- selected-results (v2) endpoint tests ----

  @Test
  void testGetSelectedRecommendationResults_WithNullBody_InjectsSelectionModeFilter()
      throws Exception {
    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-123")
            .campaignId("campaign-1")
            .recommendations(new ArrayList<>())
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/selected-results", "run-123")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(recommendationService)
        .getRecommendationResults(
            eq("run-123"),
            eq(0),
            eq(20),
            any(),
            any(),
            argThat(
                f ->
                    f != null
                        && f.getSelectionModes() != null
                        && f.getSelectionModes().contains(SelectionMode.AUTO)
                        && f.getSelectionModes().contains(SelectionMode.MANUAL)
                        && f.getSelectionModes().size() == 2));
  }

  @Test
  void
      testGetSelectedRecommendationResults_WithBodyFilters_PreservesFiltersAndInjectsSelectionMode()
          throws Exception {
    RecommendationResultFilterDTO filter = new RecommendationResultFilterDTO();
    filter.setClassifications(List.of("Digital"));
    filter.setMinFinalScore(50.0);

    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-123")
            .campaignId("campaign-1")
            .recommendations(new ArrayList<>())
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/selected-results", "run-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filter)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(recommendationService)
        .getRecommendationResults(
            eq("run-123"),
            eq(0),
            eq(20),
            any(),
            any(),
            argThat(
                f ->
                    f != null
                        && f.getSelectionModes() != null
                        && f.getSelectionModes().contains(SelectionMode.AUTO)
                        && f.getSelectionModes().contains(SelectionMode.MANUAL)
                        && f.getClassifications() != null
                        && f.getClassifications().contains("Digital")
                        && f.getMinFinalScore() == 50.0));
  }

  // ---- Selected-inventories endpoint tests ----

  @Test
  void testSelectInventories_ReturnsSuccessWithNullData() throws Exception {
    SelectedInventoriesDTO selectedInventories = new SelectedInventoriesDTO();
    selectedInventories.setInventoryIds(List.of("inventory-1", "inventory-2"));

    doNothing()
        .when(scheduleRecommendationService)
        .markInventoriesAsSelectedOnly(eq("run-123"), eq(List.of("inventory-1", "inventory-2")));

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/selected-inventories", "run-123")
                .param("operationType", "SELECT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(selectedInventories)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist());

    verify(scheduleRecommendationService)
        .markInventoriesAsSelectedOnly(eq("run-123"), eq(List.of("inventory-1", "inventory-2")));
    verify(scheduleRecommendationService, never())
        .selectInventoriesAndGetSchedules(anyString(), anyList());
    verify(scheduleRecommendationService, never()).deselectInventories(anyString(), anyList());
  }

  @Test
  void testDeselectInventories_ReturnsSuccessWithNullData() throws Exception {
    SelectedInventoriesDTO selectedInventories = new SelectedInventoriesDTO();
    selectedInventories.setInventoryIds(List.of("inventory-1"));

    doNothing()
        .when(scheduleRecommendationService)
        .deselectInventories(eq("run-123"), eq(List.of("inventory-1")));

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/selected-inventories", "run-123")
                .param("operationType", "DESELECT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(selectedInventories)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist());

    verify(scheduleRecommendationService)
        .deselectInventories(eq("run-123"), eq(List.of("inventory-1")));
  }

  @Test
  void testGetRecommendationResults_includesSpotPriceInInventoryDetails() throws Exception {
    PaginatedRecommendationResponseDTO.InventoryDetails inventoryDetails =
        PaginatedRecommendationResponseDTO.InventoryDetails.builder()
            .name("Test Billboard")
            .classification("Digital")
            .type("OOH")
            .cpmRate(2.0)
            .spotRate(0.02)
            .spotDuration(15)
            .spotsPerLoop(24)
            .loopsPerHour(10)
            .build();

    PaginatedRecommendationResponseDTO.RecommendedInventory recommendation =
        PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
            .inventoryId("inv-1")
            .referenceId("JPN-JEK-D-00000-01028")
            .name("Test Billboard")
            .finalScore(88.0)
            .inventoryDetails(inventoryDetails)
            .build();

    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-123")
            .campaignId("campaign-1")
            .recommendations(List.of(recommendation))
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/results", "run-123")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recommendations[0].inventoryDetails.spotRate").value(0.02))
        .andExpect(jsonPath("$.data.recommendations[0].inventoryDetails.cpmRate").value(2.0))
        .andExpect(jsonPath("$.data.recommendations[0].inventoryDetails.spotDuration").value(15))
        .andExpect(jsonPath("$.data.recommendations[0].inventoryDetails.loopsPerHour").value(10));
  }

  @Test
  void testGetRecommendationResults_spotPriceIsNullWhenNotAvailable() throws Exception {
    PaginatedRecommendationResponseDTO.InventoryDetails inventoryDetails =
        PaginatedRecommendationResponseDTO.InventoryDetails.builder()
            .name("Classic Billboard")
            .classification("Classic")
            .cpmRate(25.0)
            .build();

    PaginatedRecommendationResponseDTO.RecommendedInventory recommendation =
        PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
            .inventoryId("inv-2")
            .name("Classic Billboard")
            .inventoryDetails(inventoryDetails)
            .build();

    PaginatedRecommendationResponseDTO response =
        PaginatedRecommendationResponseDTO.builder()
            .runId("run-456")
            .recommendations(List.of(recommendation))
            .pagination(
                PaginatedRecommendationResponseDTO.PaginationInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build())
            .build();

    when(recommendationService.getRecommendationResults(
            anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/results", "run-456")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recommendations[0].inventoryDetails.cpmRate").value(25.0))
        .andExpect(jsonPath("$.data.recommendations[0].inventoryDetails.spotPrice").doesNotExist());
  }

  @Test
  void testSelectedInventories_MissingOperationType_ReturnsError() throws Exception {
    SelectedInventoriesDTO selectedInventories = new SelectedInventoriesDTO();
    selectedInventories.setInventoryIds(List.of("inventory-1"));

    mockMvc
        .perform(
            post("/api/v1/recommendation/runs/{runId}/selected-inventories", "run-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(selectedInventories)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void testGetSelectedResultsMeasureSummary_returnsList() throws Exception {
    SelectedResultMeasureSummaryDTO item =
        SelectedResultMeasureSummaryDTO.builder()
            .inventoryId("inv-1")
            .referenceId("ref-1")
            .cost(
                PaginatedRecommendationResponseDTO.CostEstimate.builder()
                    .estimatedCost(new java.math.BigDecimal("1500.00"))
                    .currency("USD")
                    .build())
            .forecast(
                PaginatedRecommendationResponseDTO.ForecastedMetrics.builder()
                    .estimatedImpressions(120000L)
                    .estimatedReach(80000L)
                    .build())
            .build();

    when(recommendationService.getSelectedResultsMeasureSummary("run-123"))
        .thenReturn(List.of(item));

    mockMvc
        .perform(
            get("/api/v1/recommendation/runs/{runId}/selected-results/measure-summary", "run-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].inventoryId").value("inv-1"))
        .andExpect(jsonPath("$.data[0].referenceId").value("ref-1"))
        .andExpect(jsonPath("$.data[0].cost.estimatedCost").value(1500.00))
        .andExpect(jsonPath("$.data[0].forecast.estimatedImpressions").value(120000));

    verify(recommendationService).getSelectedResultsMeasureSummary("run-123");
  }

  @Test
  void testGetSelectedResultsMeasureSummary_emptyList() throws Exception {
    when(recommendationService.getSelectedResultsMeasureSummary("run-123"))
        .thenReturn(new ArrayList<>());

    mockMvc
        .perform(
            get("/api/v1/recommendation/runs/{runId}/selected-results/measure-summary", "run-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());

    verify(recommendationService).getSelectedResultsMeasureSummary("run-123");
  }
}
