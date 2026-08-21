package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.AutoSelectionReasonCode;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.domain.RecommendationRun;
import com.mw.recommendation.engine.domain.SelectionMode;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationResultFilterDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.dto.SelectedResultMeasureSummaryDTO;
import com.mw.recommendation.engine.enums.ProgrammaticDealType;
import com.mw.recommendation.engine.enums.ProgrammaticSupport;
import com.mw.recommendation.engine.exception.BaseException;
import com.mw.recommendation.engine.repository.AudienceRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.RecommendationResultRepository;
import com.mw.recommendation.engine.repository.RecommendationRunRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private RecommendationRunRepository recommendationRunRepository;
  @Mock private RecommendationResultRepository recommendationResultRepository;
  @Mock private RecommendationAsyncService recommendationAsyncService;
  @Mock private ScoringService scoringService;
  @Mock private AudienceRepository audienceRepository;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;

  @Spy
  private java.time.Clock clock =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-06-15T00:00:00Z"), java.time.ZoneOffset.UTC);

  @InjectMocks private RecommendationService recommendationService;

  @Test
  void getRecommendationResults_sortsBySelectionMode() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);

    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), eq(null), eq(null), any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.getRecommendationResults(
        "run-123", 0, 20, List.of("selectionMode,asc"), null, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(recommendationResultRepository)
        .findByRunIdWithFilters(eq("run-123"), eq(null), eq(null), pageableCaptor.capture());

    Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("selectionMode");
    assertNotNull(order);
    assertEquals(Sort.Direction.ASC, order.getDirection());
  }

  @Test
  void getRecommendationResults_sortsBySelectionMode_whenDirectionIsSeparateToken() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);

    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), eq(null), eq(null), any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.getRecommendationResults(
        "run-123", 0, 20, List.of("selectionMode", "desc"), null, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(recommendationResultRepository)
        .findByRunIdWithFilters(eq("run-123"), eq(null), eq(null), pageableCaptor.capture());

    Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("selectionMode");
    assertNotNull(order);
    assertEquals(Sort.Direction.DESC, order.getDirection());
  }

  @Test
  void getRecommendationResults_passesProgrammaticSupportAndDealTypesFilterToRepository() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);

    RecommendationResultFilterDTO filter =
        RecommendationResultFilterDTO.builder()
            .programmaticSupport(ProgrammaticSupport.YES)
            .dealTypes(
                List.of(ProgrammaticDealType.GUARANTEED, ProgrammaticDealType.PREFERRED_DEAL))
            .build();

    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), eq(filter), eq(null), any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.getRecommendationResults("run-123", 0, 20, null, null, filter);

    ArgumentCaptor<RecommendationResultFilterDTO> filterCaptor =
        ArgumentCaptor.forClass(RecommendationResultFilterDTO.class);
    verify(recommendationResultRepository)
        .findByRunIdWithFilters(eq("run-123"), filterCaptor.capture(), eq(null), any());
    assertEquals(ProgrammaticSupport.YES, filterCaptor.getValue().getProgrammaticSupport());
    assertEquals(
        List.of(ProgrammaticDealType.GUARANTEED, ProgrammaticDealType.PREFERRED_DEAL),
        filterCaptor.getValue().getDealTypes());
  }

  @Test
  void getRecommendationResults_passesResolutionsFilterToRepository() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);

    RecommendationResultFilterDTO filter =
        RecommendationResultFilterDTO.builder()
            .resolutions(List.of("1920x1080", "1080x1920"))
            .build();

    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), eq(filter), eq(null), any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.getRecommendationResults("run-123", 0, 20, null, null, filter);

    ArgumentCaptor<RecommendationResultFilterDTO> filterCaptor =
        ArgumentCaptor.forClass(RecommendationResultFilterDTO.class);
    verify(recommendationResultRepository)
        .findByRunIdWithFilters(eq("run-123"), filterCaptor.capture(), eq(null), any());
    assertEquals(List.of("1920x1080", "1080x1920"), filterCaptor.getValue().getResolutions());
  }

  @Test
  void getRecommendationResults_passesDurationsFilterToRepository() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);

    RecommendationResultFilterDTO filter =
        RecommendationResultFilterDTO.builder().durations(List.of(10, 15, 30, 60)).build();

    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), eq(filter), eq(null), any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.getRecommendationResults("run-123", 0, 20, null, null, filter);

    ArgumentCaptor<RecommendationResultFilterDTO> filterCaptor =
        ArgumentCaptor.forClass(RecommendationResultFilterDTO.class);
    verify(recommendationResultRepository)
        .findByRunIdWithFilters(eq("run-123"), filterCaptor.capture(), eq(null), any());
    assertEquals(List.of(10, 15, 30, 60), filterCaptor.getValue().getDurations());
  }

  @Test
  void getRecommendationResults_passesResolutionsAndDurationsFilterToRepository() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);

    RecommendationResultFilterDTO filter =
        RecommendationResultFilterDTO.builder()
            .resolutions(List.of("1920x1080"))
            .durations(List.of(30))
            .build();

    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), eq(filter), eq(null), any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.getRecommendationResults("run-123", 0, 20, null, null, filter);

    ArgumentCaptor<RecommendationResultFilterDTO> filterCaptor =
        ArgumentCaptor.forClass(RecommendationResultFilterDTO.class);
    verify(recommendationResultRepository)
        .findByRunIdWithFilters(eq("run-123"), filterCaptor.capture(), eq(null), any());
    assertEquals(List.of("1920x1080"), filterCaptor.getValue().getResolutions());
    assertEquals(List.of(30), filterCaptor.getValue().getDurations());
  }

  @Test
  @SuppressWarnings("unchecked")
  void browseInventories_venueTypeIds_passedToPaginatedRepository() {
    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setCountry("MY");
    request.setStartDate(LocalDate.of(2025, 3, 1));
    request.setEndDate(LocalDate.of(2025, 5, 31));

    RecommendationRequestDTO.AudienceTargeting targeting =
        new RecommendationRequestDTO.AudienceTargeting();
    targeting.setVenueTypeIds(Map.of("digital", List.of("401", "402"), "classic", List.of("301")));
    request.setAudienceTargeting(targeting);

    when(inventoryRepository.findActiveInventoriesByCountryPaginated(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.browseInventories("campaign-001", request, 0, 20, List.of(), null);

    ArgumentCaptor<Map<String, List<String>>> venueCaptor = ArgumentCaptor.forClass(Map.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryPaginated(
            eq("MY"),
            any(),
            any(),
            venueCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any());

    Map<String, List<String>> captured = venueCaptor.getValue();
    assertNotNull(captured);
    assertEquals(List.of("401", "402"), captured.get("digital"));
    assertEquals(List.of("301"), captured.get("classic"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void browseInventories_nullAudienceTargeting_passesNullVenueTypeIds() {
    BrowseInventoryRequestDTO request = new BrowseInventoryRequestDTO();
    request.setCountry("MY");
    request.setStartDate(LocalDate.of(2025, 3, 1));
    request.setEndDate(LocalDate.of(2025, 5, 31));
    request.setAudienceTargeting(null);

    when(inventoryRepository.findActiveInventoriesByCountryPaginated(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any()))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    recommendationService.browseInventories("campaign-001", request, 0, 20, List.of(), null);

    ArgumentCaptor<Map<String, List<String>>> venueCaptor = ArgumentCaptor.forClass(Map.class);
    verify(inventoryRepository)
        .findActiveInventoriesByCountryPaginated(
            eq("MY"),
            any(),
            any(),
            venueCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            nullable(Long.class),
            any(),
            any());

    assertNull(venueCaptor.getValue());
  }

  @Test
  void getSelectedResultsMeasureSummary_returnsAutoAndManual_onlyFourFields() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);
    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));

    RecommendationResult auto =
        RecommendationResult.builder()
            .runId("run-123")
            .inventoryId("inv-auto")
            .referenceId("ref-auto")
            .name("Auto Billboard")
            .finalScore(91.0)
            .selectionMode(SelectionMode.AUTO)
            .cost(
                RecommendationResult.CostEstimate.builder()
                    .estimatedCost(new BigDecimal("1500.00"))
                    .currency("USD")
                    .costPerImpression(0.5)
                    .totalAdPlays(3000L)
                    .build())
            .forecast(
                RecommendationResult.ForecastedMetrics.builder()
                    .estimatedImpressions(120000L)
                    .estimatedReach(80000L)
                    .estimatedSov(12.5)
                    .estimatedFrequency(1.5)
                    .build())
            .build();

    RecommendationResult manual =
        RecommendationResult.builder()
            .runId("run-123")
            .inventoryId("inv-manual")
            .referenceId("ref-manual")
            .name("Manual Screen")
            .finalScore(85.0)
            .selectionMode(SelectionMode.MANUAL)
            .cost(
                RecommendationResult.CostEstimate.builder()
                    .estimatedCost(new BigDecimal("900.00"))
                    .currency("USD")
                    .build())
            .forecast(
                RecommendationResult.ForecastedMetrics.builder()
                    .estimatedImpressions(50000L)
                    .estimatedReach(40000L)
                    .build())
            .build();

    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), any(), eq(null), any()))
        .thenReturn(new PageImpl<>(List.of(auto, manual)));

    List<SelectedResultMeasureSummaryDTO> result =
        recommendationService.getSelectedResultsMeasureSummary("run-123");

    assertEquals(2, result.size());

    SelectedResultMeasureSummaryDTO autoDto = result.get(0);
    assertEquals("inv-auto", autoDto.getInventoryId());
    assertEquals("ref-auto", autoDto.getReferenceId());
    assertNotNull(autoDto.getCost());
    assertEquals(new BigDecimal("1500.00"), autoDto.getCost().getEstimatedCost());
    assertEquals("USD", autoDto.getCost().getCurrency());
    assertEquals(3000L, autoDto.getCost().getTotalAdPlays());
    assertNotNull(autoDto.getForecast());
    assertEquals(120000L, autoDto.getForecast().getEstimatedImpressions());
    assertEquals(80000L, autoDto.getForecast().getEstimatedReach());
    assertEquals(12.5, autoDto.getForecast().getEstimatedSov());

    SelectedResultMeasureSummaryDTO manualDto = result.get(1);
    assertEquals("inv-manual", manualDto.getInventoryId());
    assertEquals("ref-manual", manualDto.getReferenceId());
    assertEquals(new BigDecimal("900.00"), manualDto.getCost().getEstimatedCost());
    assertEquals(50000L, manualDto.getForecast().getEstimatedImpressions());
  }

  @Test
  void getSelectedResultsMeasureSummary_noSelected_returnsEmptyList() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);
    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), any(), eq(null), any()))
        .thenReturn(new PageImpl<>(List.of()));

    List<SelectedResultMeasureSummaryDTO> result =
        recommendationService.getSelectedResultsMeasureSummary("run-123");

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void getSelectedResultsMeasureSummary_excludesNonSelected_passesSelectionModeFilter() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.COMPLETED);
    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));
    when(recommendationResultRepository.findByRunIdWithFilters(
            eq("run-123"), any(), eq(null), any()))
        .thenReturn(new PageImpl<>(List.of()));

    recommendationService.getSelectedResultsMeasureSummary("run-123");

    ArgumentCaptor<RecommendationResultFilterDTO> filterCaptor =
        ArgumentCaptor.forClass(RecommendationResultFilterDTO.class);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(recommendationResultRepository)
        .findByRunIdWithFilters(
            eq("run-123"), filterCaptor.capture(), eq(null), pageableCaptor.capture());

    RecommendationResultFilterDTO usedFilter = filterCaptor.getValue();
    assertNotNull(usedFilter.getSelectionModes());
    assertEquals(2, usedFilter.getSelectionModes().size());
    assertTrue(usedFilter.getSelectionModes().contains(SelectionMode.AUTO));
    assertTrue(usedFilter.getSelectionModes().contains(SelectionMode.MANUAL));
    // No pagination/sort applied -> unpaged, unsorted.
    assertTrue(pageableCaptor.getValue().isUnpaged());
    assertTrue(pageableCaptor.getValue().getSort().isUnsorted());
  }

  @Test
  void getSelectedResultsMeasureSummary_runNotFound_throws() {
    when(recommendationRunRepository.findByRunId("missing")).thenReturn(Optional.empty());

    assertThrows(
        BaseException.class,
        () -> recommendationService.getSelectedResultsMeasureSummary("missing"));
  }

  @Test
  void getSelectedResultsMeasureSummary_inProgress_throws() {
    RecommendationRun run = RecommendationRun.builder().runId("run-123").build();
    run.setStatus(RecommendationRun.RunStatus.IN_PROGRESS);
    when(recommendationRunRepository.findByRunId("run-123")).thenReturn(Optional.of(run));

    assertThrows(
        BaseException.class,
        () -> recommendationService.getSelectedResultsMeasureSummary("run-123"));
  }

  // ---- Auto-selection reason passthrough on the status response ----

  private RecommendationRequestDTO buildSubmitRequest() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(LocalDate.of(2025, 1, 1));
    request.setEndDate(LocalDate.of(2025, 1, 31));
    request.setBudget(new BigDecimal("1000"));
    return request;
  }

  @Test
  void statusResponse_exposesAutoSelectionReason_whenPresentOnRun() {
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("run-reason")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.COMPLETED)
            .completionPercentage(100)
            .autoSelectionReasonCode(AutoSelectionReasonCode.MEASURE_DATA_UNAVAILABLE)
            .autoSelectionReasonDetail("0 of 5 sites had usable Measure data")
            .build();
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", buildSubmitRequest(), false);

    assertEquals("MEASURE_DATA_UNAVAILABLE", response.getAutoSelectionReasonCode());
    assertEquals("0 of 5 sites had usable Measure data", response.getAutoSelectionReasonDetail());
  }

  @Test
  void statusResponse_reasonFieldsNull_forRunsWithoutReason() {
    RecommendationRun existingRun =
        RecommendationRun.builder()
            .runId("run-legacy")
            .campaignId("campaign-001")
            .status(RecommendationRun.RunStatus.COMPLETED)
            .completionPercentage(100)
            .build();
    when(recommendationRunRepository.findByCampaignIdAndRequestHash(anyString(), anyString()))
        .thenReturn(Optional.of(existingRun));

    RecommendationStatusResponseDTO response =
        recommendationService.submitRecommendation("campaign-001", buildSubmitRequest(), false);

    assertNull(response.getAutoSelectionReasonCode());
    assertNull(response.getAutoSelectionReasonDetail());
  }
}
