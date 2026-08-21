package com.mw.recommendation.engine.v3.service;

import com.mw.recommendation.engine.v3.domain.RecommendationResultV3;
import com.mw.recommendation.engine.v3.domain.RecommendationRunV3;
import com.mw.recommendation.engine.v3.dto.V3ResultsResponseDTO;
import com.mw.recommendation.engine.v3.dto.V3StatusResponseDTO;
import com.mw.recommendation.engine.v3.repository.ResultV3Repository;
import com.mw.recommendation.engine.v3.repository.RunV3Repository;
import com.mw.recommendation.engine.v3.support.V3ErrorCode;
import com.mw.recommendation.engine.v3.support.V3Exception;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Read side of v3: run status and paginated results. */
@Service
@RequiredArgsConstructor
public class RunQueryV3Service {

  private static final Set<String> SORTABLE =
      Set.of(
          "finalScore",
          "name",
          "inventoryId",
          "referenceId",
          "confidence",
          "band",
          "selectionMode",
          "createdAt");

  private final RunV3Repository runRepository;
  private final ResultV3Repository resultRepository;

  public V3StatusResponseDTO getStatus(String runId) {
    return toStatus(requireRun(runId));
  }

  public V3ResultsResponseDTO getResults(String runId, int page, int size, List<String> sort) {
    RecommendationRunV3 run = requireRun(runId);
    if (run.getStatus() == RecommendationRunV3.RunStatus.IN_PROGRESS) {
      throw new V3Exception(
          V3ErrorCode.RUN_IN_PROGRESS,
          "Recommendation is still in progress. Current completion: "
              + run.getCompletionPercentage()
              + "%");
    }

    Page<RecommendationResultV3> resultPage =
        resultRepository.findByRunId(runId, PageRequest.of(page, size, buildSort(sort)));

    return V3ResultsResponseDTO.builder()
        .runId(runId)
        .campaignId(run.getCampaignId())
        .productId(run.getProductId())
        .companyId(run.getCompanyId())
        .recommendations(resultPage.getContent().stream().map(this::toDto).toList())
        .warnings(run.getWarnings())
        .pagination(
            V3ResultsResponseDTO.Pagination.builder()
                .page(page)
                .size(size)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .hasNext(resultPage.hasNext())
                .hasPrevious(resultPage.hasPrevious())
                .build())
        .build();
  }

  private RecommendationRunV3 requireRun(String runId) {
    return runRepository
        .findByRunId(runId)
        .orElseThrow(
            () ->
                new V3Exception(
                    V3ErrorCode.RUN_NOT_FOUND, "Recommendation run not found: " + runId));
  }

  private static Sort buildSort(List<String> sortParams) {
    if (sortParams == null || sortParams.isEmpty()) {
      return Sort.by(Sort.Direction.DESC, "finalScore");
    }
    List<Sort.Order> orders =
        sortParams.stream()
            .map(
                param -> {
                  String[] parts = param.split(",");
                  String field = SORTABLE.contains(parts[0]) ? parts[0] : "finalScore";
                  Sort.Direction dir =
                      parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                          ? Sort.Direction.ASC
                          : Sort.Direction.DESC;
                  return new Sort.Order(dir, field);
                })
            .collect(Collectors.toList());
    return Sort.by(orders);
  }

  public V3StatusResponseDTO toStatus(RecommendationRunV3 run) {
    V3StatusResponseDTO.Metadata metadata = null;
    if (run.getMetadata() != null) {
      metadata =
          V3StatusResponseDTO.Metadata.builder()
              .totalInventoriesEvaluated(run.getMetadata().getTotalInventoriesEvaluated())
              .totalInventoriesRecommended(run.getMetadata().getTotalInventoriesRecommended())
              .totalInventoriesExcluded(run.getMetadata().getTotalInventoriesExcluded())
              .exclusionReasons(run.getMetadata().getExclusionReasons())
              .averageScore(run.getMetadata().getAverageScore())
              .stageTimingsMs(run.getMetadata().getStageTimingsMs())
              .autoSelectedInventoryIds(run.getAutoSelectedInventoryIds())
              .engineVersion(run.getEngineVersion())
              .build();
    }
    return V3StatusResponseDTO.builder()
        .runId(run.getRunId())
        .status(V3StatusResponseDTO.RunStatus.valueOf(run.getStatus().name()))
        .completionPercentage(run.getCompletionPercentage())
        .campaignId(run.getCampaignId())
        .productId(run.getProductId())
        .companyId(run.getCompanyId())
        .generatedAt(run.getGeneratedAt())
        .completedAt(run.getCompletedAt())
        .seed(run.getSeed())
        .metadata(metadata)
        .warnings(run.getWarnings())
        .errorCode(run.getErrorCode())
        .errorMessage(run.getErrorMessage())
        .alternatives(
            run.getAlternatives() == null
                ? null
                : run.getAlternatives().stream()
                    .map(
                        a ->
                            V3StatusResponseDTO.Alternative.builder()
                                .inventoryId(a.getInventoryId())
                                .referenceId(a.getReferenceId())
                                .name(a.getName())
                                .city(a.getCity())
                                .distanceKm(a.getDistanceKm())
                                .estimatedDailyImpressions(a.getEstimatedDailyImpressions())
                                .build())
                    .toList())
        .cityClusters(
            run.getCityClusters() == null
                ? null
                : run.getCityClusters().stream()
                    .map(
                        c ->
                            V3StatusResponseDTO.CityCluster.builder()
                                .city(c.getCity())
                                .inventoryCount(c.getInventoryCount())
                                .averageScore(c.getAverageScore())
                                .topScore(c.getTopScore())
                                .build())
                    .toList())
        .build();
  }

  private V3ResultsResponseDTO.RecommendedInventory toDto(RecommendationResultV3 r) {
    Map<String, V3ResultsResponseDTO.ScoreAuditEntry> auditOut = null;
    if (r.getScoreAudit() != null && !r.getScoreAudit().isEmpty()) {
      auditOut = new LinkedHashMap<>();
      for (var e : r.getScoreAudit().entrySet()) {
        auditOut.put(
            e.getKey(),
            V3ResultsResponseDTO.ScoreAuditEntry.builder()
                .raw(e.getValue().getRaw())
                .normalized(e.getValue().getNormalized())
                .weight(e.getValue().getWeight())
                .weighted(e.getValue().getWeighted())
                .build());
      }
    }

    return V3ResultsResponseDTO.RecommendedInventory.builder()
        .inventoryId(r.getInventoryId())
        .referenceId(r.getReferenceId())
        .name(r.getName())
        .finalScore(r.getFinalScore())
        .band(r.getBand())
        .componentScores(
            r.getComponentScores() == null
                ? null
                : V3ResultsResponseDTO.ComponentScores.builder()
                    .measureFit(r.getComponentScores().getMeasureFit())
                    .geoFit(r.getComponentScores().getGeoFit())
                    .availability(r.getComponentScores().getAvailability())
                    .budgetFit(r.getComponentScores().getBudgetFit())
                    .audienceFit(r.getComponentScores().getAudienceFit())
                    .brandFit(r.getComponentScores().getBrandFit())
                    .qualityFit(r.getComponentScores().getQualityFit())
                    .timeFit(r.getComponentScores().getTimeFit())
                    .build())
        .scoreAudit(auditOut)
        .why(r.getWhy())
        .confidence(r.getConfidence())
        .availability(
            r.getAvailability() == null
                ? null
                : V3ResultsResponseDTO.AvailabilitySummary.builder()
                    .availableDays(r.getAvailability().getAvailableDays())
                    .totalDays(r.getAvailability().getTotalDays())
                    .availabilityPercentage(r.getAvailability().getAvailabilityPercentage())
                    .summary(r.getAvailability().getSummary())
                    .allAvailable(r.getAvailability().getAllAvailable())
                    .build())
        .forecast(
            r.getForecast() == null
                ? null
                : V3ResultsResponseDTO.Forecast.builder()
                    .estimatedImpressions(r.getForecast().getEstimatedImpressions())
                    .estimatedReach(r.getForecast().getEstimatedReach())
                    .estimatedSov(r.getForecast().getEstimatedSov())
                    .estimatedFrequency(r.getForecast().getEstimatedFrequency())
                    .source(r.getForecast().getSource())
                    .build())
        .cost(
            r.getCost() == null
                ? null
                : V3ResultsResponseDTO.Cost.builder()
                    .estimatedCost(r.getCost().getEstimatedCost())
                    .currency(r.getCost().getCurrency())
                    .costUnit(r.getCost().getCostUnit())
                    .costPerImpression(r.getCost().getCostPerImpression())
                    .totalAdPlays(r.getCost().getTotalAdPlays())
                    .prorated(r.getCost().getProrated())
                    .build())
        .selectionMode(r.getSelectionMode())
        .inventoryDetails(
            r.getInventoryDetails() == null
                ? null
                : V3ResultsResponseDTO.InventoryDetails.builder()
                    .classification(r.getInventoryDetails().getClassification())
                    .type(r.getInventoryDetails().getType())
                    .format(r.getInventoryDetails().getFormat())
                    .city(r.getInventoryDetails().getCity())
                    .state(r.getInventoryDetails().getState())
                    .address(r.getInventoryDetails().getAddress())
                    .mediaOwnerName(r.getInventoryDetails().getMediaOwnerName())
                    .venueTypes(r.getInventoryDetails().getVenueTypes())
                    .latitude(r.getInventoryDetails().getLatitude())
                    .longitude(r.getInventoryDetails().getLongitude())
                    .size(r.getInventoryDetails().getSize())
                    .inventoryCluster(r.getInventoryDetails().getInventoryCluster())
                    .build())
        .build();
  }
}
