package com.mw.recommendation.engine.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;

/**
 * Unit tests for RecommendationRun entity focusing on database optimization. Verifies that compound
 * indexes are properly configured for optimal query performance.
 */
@DisplayName("RecommendationRun - Database Index Optimization Tests")
class RecommendationRunIndexTest {

  @Test
  @DisplayName("Should have compound index on campaignId and requestHash for deduplication")
  void testHasCompoundIndexForCampaignAndRequestHash() {
    // Verify the @CompoundIndex annotation exists on the class
    CompoundIndex[] compoundIndexes =
        RecommendationRun.class.getAnnotationsByType(CompoundIndex.class);

    assertNotNull(compoundIndexes, "CompoundIndex annotations should be present");
    assertTrue(compoundIndexes.length > 0, "At least one compound index should be defined");

    // Find the campaign_request_idx compound index
    CompoundIndex campaignRequestIndex = null;
    for (CompoundIndex index : compoundIndexes) {
      if ("campaign_request_idx".equals(index.name())) {
        campaignRequestIndex = index;
        break;
      }
    }

    assertNotNull(
        campaignRequestIndex,
        "Compound index 'campaign_request_idx' should be defined for performance optimization");

    // Verify the index definition includes both campaignId and requestHash
    String indexDef = campaignRequestIndex.def();
    assertTrue(
        indexDef.contains("campaignId"),
        "Index definition should include 'campaignId' field: " + indexDef);
    assertTrue(
        indexDef.contains("requestHash"),
        "Index definition should include 'requestHash' field: " + indexDef);

    // Verify the order (campaignId first, then requestHash)
    int campaignIdPos = indexDef.indexOf("campaignId");
    int requestHashPos = indexDef.indexOf("requestHash");
    assertTrue(
        campaignIdPos < requestHashPos,
        "campaignId should appear before requestHash in index definition for optimal query"
            + " performance");
  }

  @Test
  @DisplayName("Should have individual indexes on campaignId and requestHash for flexibility")
  void testHasIndividualIndexes() {
    // Verify individual @Indexed fields still exist for other query patterns
    try {
      var campaignIdField = RecommendationRun.class.getDeclaredField("campaignId");
      assertNotNull(
          campaignIdField.getAnnotation(org.springframework.data.mongodb.core.index.Indexed.class),
          "campaignId should have @Indexed annotation");

      var requestHashField = RecommendationRun.class.getDeclaredField("requestHash");
      assertNotNull(
          requestHashField.getAnnotation(org.springframework.data.mongodb.core.index.Indexed.class),
          "requestHash should have @Indexed annotation");
    } catch (NoSuchFieldException e) {
      fail("Expected fields not found: " + e.getMessage());
    }
  }

  @Test
  @DisplayName("Should have unique index on runId")
  void testHasUniqueIndexOnRunId() {
    try {
      var runIdField = RecommendationRun.class.getDeclaredField("runId");
      var indexedAnnotation =
          runIdField.getAnnotation(org.springframework.data.mongodb.core.index.Indexed.class);

      assertNotNull(indexedAnnotation, "runId should have @Indexed annotation");
      assertTrue(indexedAnnotation.unique(), "runId index should be unique");
    } catch (NoSuchFieldException e) {
      fail("runId field not found: " + e.getMessage());
    }
  }

  @Test
  @DisplayName(
      "Should maintain backward compatibility - all previously indexed fields still indexed")
  void testBackwardCompatibilityOfIndexes() {
    // Ensure optimization didn't remove any existing indexes
    String[] expectedIndexedFields = {
      "runId", "campaignId", "productId", "companyId", "requestHash"
    };

    for (String fieldName : expectedIndexedFields) {
      try {
        var field = RecommendationRun.class.getDeclaredField(fieldName);
        var indexedAnnotation =
            field.getAnnotation(org.springframework.data.mongodb.core.index.Indexed.class);
        assertNotNull(
            indexedAnnotation, "Field '" + fieldName + "' should maintain its @Indexed annotation");
      } catch (NoSuchFieldException e) {
        fail("Expected indexed field '" + fieldName + "' not found");
      }
    }
  }
}
