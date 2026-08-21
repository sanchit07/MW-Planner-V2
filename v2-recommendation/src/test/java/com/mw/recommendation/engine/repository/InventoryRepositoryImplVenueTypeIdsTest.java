package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - buildVenueTypeIdsCriteria")
class InventoryRepositoryImplVenueTypeIdsTest {

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private InventoryRepositoryImpl repo;

  private List<Document> getOrClauses(Criteria criteria) {
    return criteria.getCriteriaObject().getList("$or", Document.class);
  }

  private boolean hasClassificationAndIds(
      List<Document> orClauses, String classification, List<String> ids) {
    return orClauses.stream()
        .anyMatch(
            doc -> {
              Object classValue = doc.get("classification");
              if (!(classValue instanceof String)) return false; // skip nin-clause docs
              if (!classification.equals(classValue)) return false;
              Object venueField = doc.get("venueTypeIds");
              if (!(venueField instanceof Document)) return false;
              List<String> inList = ((Document) venueField).getList("$in", String.class);
              return ids.equals(inList);
            });
  }

  @SuppressWarnings("unchecked")
  private List<String> getNinClassifications(List<Document> orClauses) {
    return orClauses.stream()
        .filter(doc -> doc.containsKey("classification") && !doc.containsKey("venueTypeIds"))
        .findFirst()
        .map(doc -> ((Document) doc.get("classification")).getList("$nin", String.class))
        .orElse(List.of());
  }

  @Test
  @DisplayName("digital and classic with IDs → per-classification OR clauses + nin fallback")
  void digitalAndClassic_withIds_buildsCorrectOr() {
    Map<String, List<String>> venueTypeIds =
        Map.of("digital", List.of("401", "402"), "classic", List.of("301"));

    Criteria criteria = repo.buildVenueTypeIdsCriteria(venueTypeIds);
    List<Document> orClauses = getOrClauses(criteria);

    assertEquals(3, orClauses.size());
    assertTrue(hasClassificationAndIds(orClauses, "Digital", List.of("401", "402")));
    assertTrue(hasClassificationAndIds(orClauses, "Classic", List.of("301")));
    List<String> nin = getNinClassifications(orClauses);
    assertTrue(nin.contains("Digital"));
    assertTrue(nin.contains("Classic"));
  }

  @Test
  @DisplayName("digital empty list → digital not targeted, passes through via nin fallback")
  void digitalEmptyList_digitalPassesThrough() {
    Map<String, List<String>> venueTypeIds = new HashMap<>();
    venueTypeIds.put("digital", List.of());
    venueTypeIds.put("classic", List.of("301", "302"));

    Criteria criteria = repo.buildVenueTypeIdsCriteria(venueTypeIds);
    List<Document> orClauses = getOrClauses(criteria);

    // Only classic clause + nin fallback (digital skipped)
    assertEquals(2, orClauses.size());
    assertTrue(hasClassificationAndIds(orClauses, "Classic", List.of("301", "302")));
    List<String> nin = getNinClassifications(orClauses);
    assertFalse(nin.contains("Digital"), "Digital must NOT be in nin — it should pass through");
    assertTrue(nin.contains("Classic"));
  }

  @Test
  @DisplayName("digital null value → digital not targeted, passes through via nin fallback")
  void digitalNullValue_digitalPassesThrough() {
    Map<String, List<String>> venueTypeIds = new HashMap<>();
    venueTypeIds.put("digital", null);
    venueTypeIds.put("classic", List.of("301"));

    Criteria criteria = repo.buildVenueTypeIdsCriteria(venueTypeIds);
    List<Document> orClauses = getOrClauses(criteria);

    assertEquals(2, orClauses.size());
    assertTrue(hasClassificationAndIds(orClauses, "Classic", List.of("301")));
    List<String> nin = getNinClassifications(orClauses);
    assertFalse(nin.contains("Digital"), "Digital must NOT be in nin — it should pass through");
  }

  @Test
  @DisplayName("classic only (no digital key) → digital passes through via nin fallback")
  void classicOnly_noDigitalKey_digitalPassesThrough() {
    Map<String, List<String>> venueTypeIds = Map.of("classic", List.of("301"));

    Criteria criteria = repo.buildVenueTypeIdsCriteria(venueTypeIds);
    List<Document> orClauses = getOrClauses(criteria);

    assertEquals(2, orClauses.size());
    assertTrue(hasClassificationAndIds(orClauses, "Classic", List.of("301")));
    List<String> nin = getNinClassifications(orClauses);
    assertFalse(nin.contains("Digital"));
    assertTrue(nin.contains("Classic"));
  }

  @Test
  @DisplayName("key capitalized correctly — 'digital' maps to 'Digital', 'classic' to 'Classic'")
  void keyCapitalization_isCorrect() {
    Map<String, List<String>> venueTypeIds =
        Map.of("digital", List.of("401"), "classic", List.of("301"));

    Criteria criteria = repo.buildVenueTypeIdsCriteria(venueTypeIds);
    List<Document> orClauses = getOrClauses(criteria);

    assertTrue(hasClassificationAndIds(orClauses, "Digital", List.of("401")));
    assertTrue(hasClassificationAndIds(orClauses, "Classic", List.of("301")));
    assertFalse(hasClassificationAndIds(orClauses, "digital", List.of("401")));
    assertFalse(hasClassificationAndIds(orClauses, "classic", List.of("301")));
  }

  @Test
  @DisplayName("findActiveInventoriesByCountryPaginated — venueTypeIds criteria applied to query")
  @SuppressWarnings("unchecked")
  void paginatedMethod_venueTypeIds_appliedToQuery() {
    when(mongoTemplate.count(any(Query.class), eq(Inventory.class))).thenReturn(0L);
    when(mongoTemplate.find(any(Query.class), eq(Inventory.class))).thenReturn(List.of());

    BrowseInventoryRequestDTO filterRequest = new BrowseInventoryRequestDTO();
    filterRequest.setCountry("MY");
    filterRequest.setStartDate(LocalDate.of(2025, 1, 1));
    filterRequest.setEndDate(LocalDate.of(2025, 3, 31));

    Map<String, List<String>> venueTypeIds = Map.of("digital", List.of("401", "402"));

    repo.findActiveInventoriesByCountryPaginated(
        "MY",
        null,
        null,
        venueTypeIds,
        null,
        null,
        null,
        filterRequest,
        null,
        PageRequest.of(0, 20),
        null);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(Inventory.class));

    String queryJson = queryCaptor.getValue().getQueryObject().toJson();
    assertTrue(queryJson.contains("$or"), "Query must contain $or from venueTypeIds criteria");
    assertTrue(queryJson.contains("venueTypeIds"), "Query must filter on venueTypeIds field");
    assertTrue(queryJson.contains("Digital"), "Query must contain capitalized classification key");
  }

  @Test
  @DisplayName("findActiveInventoriesByCountryPaginated — null venueTypeIds skips venue criteria")
  @SuppressWarnings("unchecked")
  void paginatedMethod_nullVenueTypeIds_noVenueCriteria() {
    when(mongoTemplate.count(any(Query.class), eq(Inventory.class))).thenReturn(0L);
    when(mongoTemplate.find(any(Query.class), eq(Inventory.class))).thenReturn(List.of());

    BrowseInventoryRequestDTO filterRequest = new BrowseInventoryRequestDTO();
    filterRequest.setCountry("MY");
    filterRequest.setStartDate(LocalDate.of(2025, 1, 1));
    filterRequest.setEndDate(LocalDate.of(2025, 3, 31));

    repo.findActiveInventoriesByCountryPaginated(
        "MY", null, null, null, null, null, null, filterRequest, null, PageRequest.of(0, 20), null);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(Inventory.class));

    String queryJson = queryCaptor.getValue().getQueryObject().toJson();
    assertFalse(
        queryJson.contains("venueTypeIds"), "Query must NOT contain venueTypeIds when null");
  }
}
