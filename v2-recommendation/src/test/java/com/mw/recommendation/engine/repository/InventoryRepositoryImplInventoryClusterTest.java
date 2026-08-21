package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.InventoryAttributeFilters;
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
 * Inventory-cluster filter on the RECOMMENDATIONS (scored) fetch: match-any ($in) against the
 * top-level {@code inventoryCluster} array. Optional — a stage is added only when a value is
 * present. Mirrors {@link InventoryRepositoryImplScoredAttributeFiltersTest}: mocks MongoTemplate,
 * inspects the captured aggregation pipeline.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - scored fetch inventoryCluster filter")
class InventoryRepositoryImplInventoryClusterTest {

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
  @DisplayName("single cluster → $in on top-level inventoryCluster")
  void singleCluster_matchesTopLevel() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().inventoryCluster(List.of("premium")).build());
    assertTrue(json.contains("\"inventoryCluster\":{\"$in\":[\"premium\"]}"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("multiple clusters → all present in $in")
  void multipleClusters_allPresent() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder()
                .inventoryCluster(List.of("premium", "transit"))
                .build());
    assertTrue(
        json.contains("\"inventoryCluster\":{\"$in\":[\"premium\",\"transit\"]}"),
        "Pipeline: " + json);
  }

  @Test
  @DisplayName("empty cluster list → no inventoryCluster stage")
  void emptyCluster_noStage() {
    String json =
        runAndCaptureAggregationJson(
            InventoryAttributeFilters.builder().inventoryCluster(Collections.emptyList()).build());
    assertFalse(json.contains("inventoryCluster"), "Empty list adds no filter. Pipeline: " + json);
  }

  @Test
  @DisplayName("null attribute filters → no inventoryCluster stage")
  void nullAttrs_noStage() {
    String json = runAndCaptureAggregationJson(null);
    assertFalse(json.contains("inventoryCluster"), "Pipeline: " + json);
  }

  @Test
  @DisplayName("isEmpty() reflects only-inventoryCluster state")
  void isEmpty_reflectsClusterOnly() {
    assertFalse(
        InventoryAttributeFilters.builder().inventoryCluster(List.of("premium")).build().isEmpty(),
        "A set inventoryCluster must make isEmpty() false");
    assertTrue(
        InventoryAttributeFilters.builder()
            .inventoryCluster(Collections.emptyList())
            .build()
            .isEmpty(),
        "An empty inventoryCluster (nothing else set) must keep isEmpty() true");
    assertTrue(
        InventoryAttributeFilters.builder().build().isEmpty(),
        "No filters set must keep isEmpty() true");
  }
}
