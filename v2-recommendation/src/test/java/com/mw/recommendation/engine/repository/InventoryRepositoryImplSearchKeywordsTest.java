package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRepositoryImpl - buildSearchKeywordsCriteria")
class InventoryRepositoryImplSearchKeywordsTest {

  private static final List<String> EXPECTED_FIELDS =
      List.of("address", "locationHierarchy.cityName", "locationHierarchy.stateName");

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private InventoryRepositoryImpl repo;

  private List<Document> getOrClauses(Criteria criteria) {
    return criteria.getCriteriaObject().getList("$or", Document.class);
  }

  @Test
  @DisplayName("one keyword → one case-insensitive quoted regex clause per target field")
  void singleKeyword_buildsOrAcrossAllFields() {
    Criteria criteria = repo.buildSearchKeywordsCriteria(List.of("Kuala Lumpur"));
    List<Document> orClauses = getOrClauses(criteria);

    assertEquals(EXPECTED_FIELDS.size(), orClauses.size());
    for (String field : EXPECTED_FIELDS) {
      Pattern p =
          orClauses.stream()
              .map(doc -> doc.get(field, Pattern.class))
              .filter(java.util.Objects::nonNull)
              .findFirst()
              .orElseThrow(() -> new AssertionError("missing clause for " + field));
      assertEquals(Pattern.quote("Kuala Lumpur"), p.pattern());
      assertTrue((p.flags() & Pattern.CASE_INSENSITIVE) != 0);
    }
  }

  @Test
  @DisplayName("two keywords → OR semantics: fields × keywords clauses")
  void multipleKeywords_orSemantics() {
    Criteria criteria = repo.buildSearchKeywordsCriteria(List.of("Kuala Lumpur", "Cyberjaya"));
    assertEquals(EXPECTED_FIELDS.size() * 2, getOrClauses(criteria).size());
  }

  @Test
  @DisplayName("keyword is trimmed and regex-escaped (special chars literal)")
  void keywordIsTrimmedAndQuoted() {
    Criteria criteria = repo.buildSearchKeywordsCriteria(List.of("  KLCC (Tower 1) "));
    Pattern p = getOrClauses(criteria).get(0).get("address", Pattern.class);
    assertEquals(Pattern.quote("KLCC (Tower 1)"), p.pattern());
  }

  @Test
  @DisplayName("null / empty / all-blank input → null (caller adds no stage)")
  void degenerateInput_returnsNull() {
    assertNull(repo.buildSearchKeywordsCriteria(null));
    assertNull(repo.buildSearchKeywordsCriteria(List.of()));
    assertNull(repo.buildSearchKeywordsCriteria(Arrays.asList("  ", null)));
  }
}
