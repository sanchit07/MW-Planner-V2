package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.dto.CampaignInventoryFilterDTO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Unit tests for {@link InventoryRepositoryImpl#buildSearchKeywordsCriteria(List)} — the keyword
 * search criteria used by geofencing named-location filtering. No Mongo/Spring context is needed;
 * the method builds a pure {@link Criteria} object, so the impl is constructed with null
 * collaborators.
 */
class InventoryRepositoryImplTest {

  // Only the location fields are searched; the impl currently comments out name/referenceId.
  private static final List<String> KEYWORD_FIELDS =
      List.of("location.address", "location.city", "location.state");

  private InventoryRepositoryImpl inventoryRepositoryImpl;

  @BeforeEach
  void setUp() {
    // buildSearchKeywordsCriteria touches none of the collaborators.
    inventoryRepositoryImpl = new InventoryRepositoryImpl(null, null, null);
  }

  @Test
  void buildSearchKeywordsCriteria_shouldReturnNull_whenNull() {
    assertThat(inventoryRepositoryImpl.buildSearchKeywordsCriteria(null)).isNull();
  }

  @Test
  void buildSearchKeywordsCriteria_shouldReturnNull_whenEmpty() {
    assertThat(inventoryRepositoryImpl.buildSearchKeywordsCriteria(Collections.emptyList()))
        .isNull();
  }

  @Test
  void buildSearchKeywordsCriteria_shouldReturnNull_whenOnlyBlankOrNullKeywords() {
    List<String> keywords = new ArrayList<>();
    keywords.add(null);
    keywords.add("");
    keywords.add("   ");
    assertThat(inventoryRepositoryImpl.buildSearchKeywordsCriteria(keywords)).isNull();
  }

  @Test
  void buildSearchKeywordsCriteria_shouldBuildOrAcrossAllFields_forSingleKeyword() {
    Criteria criteria = inventoryRepositoryImpl.buildSearchKeywordsCriteria(List.of("Singapore"));

    assertThat(criteria).isNotNull();
    List<Document> orClauses = orClausesOf(criteria);

    // One clause per searchable field.
    assertThat(orClauses).hasSize(KEYWORD_FIELDS.size());
    assertThat(orClauses.stream().map(this::soleFieldOf).toList())
        .containsExactlyElementsOf(KEYWORD_FIELDS);

    // Every clause is a case-insensitive, literally-quoted regex on "Singapore".
    for (Document clause : orClauses) {
      Pattern pattern = (Pattern) clause.get(soleFieldOf(clause));
      assertThat(pattern.pattern()).isEqualTo(Pattern.quote("Singapore"));
      assertThat(pattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
    }
  }

  @Test
  void buildSearchKeywordsCriteria_shouldExpandClausesPerKeyword_forMultipleKeywords() {
    Criteria criteria =
        inventoryRepositoryImpl.buildSearchKeywordsCriteria(Arrays.asList("Singapore", "Delhi"));

    assertThat(criteria).isNotNull();
    List<Document> orClauses = orClausesOf(criteria);

    // Two keywords x five fields.
    assertThat(orClauses).hasSize(KEYWORD_FIELDS.size() * 2);

    List<String> patterns =
        orClauses.stream().map(c -> ((Pattern) c.get(soleFieldOf(c))).pattern()).toList();
    assertThat(patterns).contains(Pattern.quote("Singapore"), Pattern.quote("Delhi"));
  }

  @Test
  void buildSearchKeywordsCriteria_shouldTrimAndSkipBlankKeywords() {
    Criteria criteria =
        inventoryRepositoryImpl.buildSearchKeywordsCriteria(
            Arrays.asList("  Singapore  ", "  ", null, "Delhi"));

    assertThat(criteria).isNotNull();
    List<Document> orClauses = orClausesOf(criteria);

    // Only the two non-blank keywords survive; each is trimmed.
    assertThat(orClauses).hasSize(KEYWORD_FIELDS.size() * 2);
    List<String> patterns =
        orClauses.stream().map(c -> ((Pattern) c.get(soleFieldOf(c))).pattern()).toList();
    assertThat(patterns).contains(Pattern.quote("Singapore"), Pattern.quote("Delhi"));
    assertThat(patterns).doesNotContain(Pattern.quote("  Singapore  "));
  }

  @Test
  void buildSearchKeywordsCriteria_shouldEscapeRegexMetacharacters() {
    Criteria criteria =
        inventoryRepositoryImpl.buildSearchKeywordsCriteria(List.of("St. John's (Downtown)"));

    List<Document> orClauses = orClausesOf(criteria);
    Pattern pattern = (Pattern) orClauses.getFirst().get(soleFieldOf(orClauses.getFirst()));
    // Pattern.quote wraps the literal in \Q...\E so metacharacters are matched literally.
    assertThat(pattern.pattern()).isEqualTo(Pattern.quote("St. John's (Downtown)"));
  }

  @Test
  void buildFilterCriteria_shouldQuoteNameRegex_soMetacharactersMatchLiterally() {
    // A name containing an unclosed regex group ("DBKK (") previously threw
    // PatternSyntaxException -> HTTP 400. Pattern.quote must make it a literal match.
    CampaignInventoryFilterDTO filter = new CampaignInventoryFilterDTO();
    filter.setName("DBKK (");

    List<Criteria> criteriaList = inventoryRepositoryImpl.buildFilterCriteria(filter);

    Pattern namePattern = nameRegexOf(criteriaList);
    assertThat(namePattern.pattern()).isEqualTo(Pattern.quote("DBKK ("));
    assertThat(namePattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
    // Sanity: the quoted pattern compiles and matches the intended inventory literally.
    assertThat(namePattern.matcher("DBKK (Kota Kinabalu City Hall)").find()).isTrue();
  }

  @Test
  void buildFilterCriteria_shouldQuotePlainName_preservingContainsBehavior() {
    CampaignInventoryFilterDTO filter = new CampaignInventoryFilterDTO();
    filter.setName("DBKK");

    Pattern namePattern = nameRegexOf(inventoryRepositoryImpl.buildFilterCriteria(filter));
    assertThat(namePattern.pattern()).isEqualTo(Pattern.quote("DBKK"));
    assertThat(namePattern.matcher("DBKK (Kota Kinabalu City Hall)").find()).isTrue();
  }

  @Test
  void buildFilterCriteria_shouldOmitNameCriterion_whenNameBlankOrNull() {
    CampaignInventoryFilterDTO nullName = new CampaignInventoryFilterDTO();
    assertThat(hasNameCriterion(inventoryRepositoryImpl.buildFilterCriteria(nullName))).isFalse();

    CampaignInventoryFilterDTO blankName = new CampaignInventoryFilterDTO();
    blankName.setName("   ");
    assertThat(hasNameCriterion(inventoryRepositoryImpl.buildFilterCriteria(blankName))).isFalse();
  }

  /** Extract the compiled {@code name} regex from the built criteria list. */
  private Pattern nameRegexOf(List<Criteria> criteriaList) {
    for (Criteria criteria : criteriaList) {
      Document doc = criteria.getCriteriaObject();
      if (doc.get("name") instanceof Pattern pattern) {
        return pattern;
      }
      // name is now combined with referenceId via an $or clause.
      if (doc.get("$or") instanceof List<?> orClauses) {
        for (Object clause : orClauses) {
          if (clause instanceof Document clauseDoc
              && clauseDoc.get("name") instanceof Pattern pattern) {
            return pattern;
          }
        }
      }
    }
    throw new AssertionError("No 'name' regex criterion found: " + criteriaList);
  }

  private boolean hasNameCriterion(List<Criteria> criteriaList) {
    return criteriaList.stream().anyMatch(c -> c.getCriteriaObject().containsKey("name"));
  }

  @SuppressWarnings("unchecked")
  private List<Document> orClausesOf(Criteria criteria) {
    Document doc = criteria.getCriteriaObject();
    assertThat(doc).containsKey("$or");
    return (List<Document>) doc.get("$or");
  }

  private String soleFieldOf(Document clause) {
    assertThat(clause.keySet()).hasSize(1);
    return clause.keySet().iterator().next();
  }
}
