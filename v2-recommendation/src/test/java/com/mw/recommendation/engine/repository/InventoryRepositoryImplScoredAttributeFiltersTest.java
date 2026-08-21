package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.InventoryAttributeFilters;
import com.mw.recommendation.engine.enums.ProgrammaticSupport;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

/**
 * Inventory-attribute filters on the RECOMMENDATIONS (scored) fetch: Inventory Format ({@code
 * format}), Resolution ({@code panels} pixel dims), Creative Type ({@code
 * creativeFormats.creativeType}), DSP ({@code dsps}) and Purchase Type ({@code
 * programmaticDealTypes}). Each EXACT-matches a real inventory field and is optional. Mirrors
 * {@link InventoryRepositoryImplGoalPricingTest}: mocks MongoTemplate, inspects the pipeline.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - scored fetch inventory-attribute filters")
class InventoryRepositoryImplScoredAttributeFiltersTest {

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private InventoryRepositoryImpl repo;

  @SuppressWarnings("unchecked")
  private String runAndCaptureAggregationJson(InventoryAttributeFilters attrs) {
    AggregationResults<Inventory> empty = mock(AggregationResults.class);
    when(empty.getMappedResults()).thenReturn(Collections.emptyList());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("inventories"), eq(Inventory.class)))
        .thenReturn(empty);

    repo.findActiveInventoriesByCountryWithGeographyTargeting(
        "IN", null, null, null, null, null, null, null, null, null, null, null, null, attrs);

    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    return aggCaptor.getValue().toString().replace(" ", "");
  }

  @Test
  @DisplayName("format → $in on top-level format")
  void format_matchesTopLevelFormat() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().formats(List.of("billboard")).build());
    assertTrue(json.contains("\"format\":{\"$in\":[\"billboard\"]}"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("creativeTypes → $in on nested creativeFormats.creativeType")
  void creativeTypes_matchesNested() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().creativeTypes(List.of("video", "image")).build());
    assertTrue(
        json.contains("\"creativeFormats.creativeType\":{\"$in\":[\"video\",\"image\"]}"),
        "Pipeline: " + json);
  }

  @Test
  @DisplayName("dsps → $in on dsps")
  void dsps_matches() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().dsps(List.of("LMX-ECOMMERCE")).build());
    assertTrue(json.contains("\"dsps\":{\"$in\":[\"LMX-ECOMMERCE\"]}"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("dealTypes → $in on programmaticDealTypes, lowercased")
  void dealTypes_lowercased() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().dealTypes(List.of("GUARANTEED")).build());
    assertTrue(
        json.contains("\"programmaticDealTypes\":{\"$in\":[\"guaranteed\"]}"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("resolution → panels $elemMatch on pixelWidth/pixelHeight")
  void resolution_matchesPanelDims() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().resolutions(List.of("1920x1080")).build());
    assertTrue(
        json.contains("\"panels\":{\"$elemMatch\":{\"pixelWidth\":1920,\"pixelHeight\":1080}}"),
        "Pipeline: " + json);
  }

  @Test
  @DisplayName("multiple resolutions → $or of panels $elemMatch")
  void multipleResolutions_orOfElemMatch() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder()
                .resolutions(List.of("1920x1080", "1600x1260"))
                .build());
    assertTrue(json.contains("\"$or\""), "Multiple resolutions must OR. Pipeline: " + json);
    assertTrue(json.contains("\"pixelWidth\":1920"), "Pipeline: " + json);
    assertTrue(json.contains("\"pixelWidth\":1600"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("unparseable resolution → no panels stage")
  void badResolution_noStage() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().resolutions(List.of("not-a-res")).build());
    assertFalse(json.contains("\"pixelWidth\""), "Pipeline: " + json);
  }

  @Test
  @DisplayName("programmaticSupport YES → require any programmaticDealTypes")
  void programmaticSupportYes_requiresAny() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder()
                .programmaticSupport(ProgrammaticSupport.YES)
                .build());
    assertTrue(
        json.contains("\"programmaticDealTypes.0\":{\"$exists\":true}"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("programmaticSupport NO → forbid programmaticDealTypes ($or exists/null/size 0)")
  void programmaticSupportNo_forbidsAny() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder()
                .programmaticSupport(ProgrammaticSupport.NO)
                .build());
    assertTrue(json.contains("\"$or\""), "Pipeline: " + json);
    assertTrue(json.contains("\"programmaticDealTypes\":{\"$exists\":false}"), "Pipeline: " + json);
    assertTrue(json.contains("\"$size\":0"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("programmaticSupport ALL → no stage")
  void programmaticSupportAll_noStage() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder()
                .programmaticSupport(ProgrammaticSupport.ALL)
                .build());
    assertFalse(json.contains("programmaticDealTypes"), "ALL adds no filter. Pipeline: " + json);
  }

  @Test
  @DisplayName("null attribute filters → none of the attribute stages present")
  void nullAttrs_noStages() {
    String json = runAndCaptureAggregationJson(null);
    assertFalse(json.contains("creativeFormats.creativeType"), json);
    assertFalse(json.contains("\"dsps\""), json);
    assertFalse(json.contains("programmaticDealTypes"), json);
    assertFalse(json.contains("pixelWidth"), json);
  }
}
