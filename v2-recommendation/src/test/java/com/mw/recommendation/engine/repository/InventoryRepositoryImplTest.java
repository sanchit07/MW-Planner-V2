package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.enums.ProgrammaticSupport;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Verifies classification-aware pricing filter: Digital inventories require CPM &gt; 0, while
 * Classic/Transit/other classifications only require a non-empty prices array. The filter is a
 * single $or applied to every fetch, so Classic is never excluded by the CPM requirement.
 */
@ExtendWith(MockitoExtension.class)
class InventoryRepositoryImplTest {

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private InventoryRepositoryImpl repository;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    // Use lenient() to avoid UnnecessaryStubbingException — each test only exercises one path
    AggregationResults<Inventory> emptyResults = mock(AggregationResults.class);
    lenient().when(emptyResults.getMappedResults()).thenReturn(Collections.emptyList());
    lenient()
        .when(
            mongoTemplate.aggregate(any(Aggregation.class), eq("inventories"), eq(Inventory.class)))
        .thenReturn(emptyResults);

    lenient()
        .when(mongoTemplate.find(any(Query.class), eq(Inventory.class)))
        .thenReturn(Collections.emptyList());
    lenient().when(mongoTemplate.count(any(Query.class), eq(Inventory.class))).thenReturn(0L);
  }

  private String capturePaginatedQueryJson(List<String> classifications) {
    return capturePaginatedQueryJson(classifications, null);
  }

  private String capturePaginatedQueryJson(List<String> classifications, String search) {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    repository.findActiveInventoriesByCountryPaginated(
        "Japan",
        null,
        null,
        null,
        null,
        classifications,
        search,
        null,
        null,
        PageRequest.of(0, 10),
        null);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(Inventory.class));
    return queryCaptor.getValue().getQueryObject().toJson();
  }

  private String capturePaginatedQueryJson(
      List<String> classifications,
      String search,
      RecommendationRequestDTO.GeographyTargeting geo,
      BrowseInventoryRequestDTO filterRequest) {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    repository.findActiveInventoriesByCountryPaginated(
        "Japan",
        null,
        geo,
        null,
        null,
        classifications,
        search,
        filterRequest,
        null,
        PageRequest.of(0, 10),
        null);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(Inventory.class));
    return queryCaptor.getValue().getQueryObject().toJson();
  }

  private String capturePaginatedQueryJsonWithGoal(RecommendationRequestDTO.CampaignGoal goal) {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    repository.findActiveInventoriesByCountryPaginated(
        "Japan",
        null,
        null,
        null,
        null,
        List.of("Digital"),
        null,
        null,
        null,
        PageRequest.of(0, 10),
        goal);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(Inventory.class));
    return queryCaptor.getValue().getQueryObject().toJson();
  }

  private String captureAggregationJson(List<String> classifications) {
    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    repository.findActiveInventoriesByCountryWithGeographyTargeting(
        "Japan",
        null,
        null,
        null,
        null,
        classifications,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false);

    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    return aggCaptor.getValue().toString();
  }

  private String captureAggregationJsonWithGoal(RecommendationRequestDTO.CampaignGoal goal) {
    ArgumentCaptor<Aggregation> aggCaptor = ArgumentCaptor.forClass(Aggregation.class);
    repository.findActiveInventoriesByCountryWithGeographyTargeting(
        "Japan",
        null,
        null,
        null,
        null,
        List.of("Digital"),
        null,
        null,
        null,
        null,
        null,
        goal,
        null,
        false);

    verify(mongoTemplate).aggregate(aggCaptor.capture(), eq("inventories"), eq(Inventory.class));
    return aggCaptor.getValue().toString();
  }

  // -------------------------------------------------------------------------
  // findActiveInventoriesByCountryPaginated — classification-aware pricing
  // -------------------------------------------------------------------------

  @Test
  void cpm_requirement_scoped_to_digital_when_classifications_null() {
    String queryJson = capturePaginatedQueryJson(null);

    assertTrue(queryJson.contains("cpm"), "Digital arm of pricing $or must require CPM");
    assertTrue(
        queryJson.contains("$ne") && queryJson.contains("Digital"),
        "Non-Digital arm must exist so Classic/Transit pass without CPM");
    assertTrue(queryJson.contains("prices"), "Both arms constrain the prices field");
  }

  @Test
  void classic_classification_not_excluded_by_cpm_requirement() {
    String queryJson = capturePaginatedQueryJson(List.of("Classic"));

    // CPM constraint exists but only inside the Digital arm — Classic matches the $ne arm
    assertTrue(
        queryJson.contains("$ne"),
        "Non-Digital arm must be present so Classic passes with any price model");
    assertTrue(
        queryJson.contains("Classic"), "Classification filter for Classic must still be applied");
  }

  @Test
  void transit_classification_not_excluded_by_cpm_requirement() {
    String queryJson = capturePaginatedQueryJson(List.of("Transit"));

    assertTrue(
        queryJson.contains("$ne"),
        "Non-Digital arm must be present so Transit passes with any price model");
    assertTrue(
        queryJson.contains("Transit"), "Classification filter for Transit must still be applied");
  }

  @Test
  void mixed_digital_and_classic_keeps_cpm_for_digital_only() {
    String queryJson = capturePaginatedQueryJson(List.of("Digital", "Classic"));

    // Digital arm keeps CPM requirement even when Classic is also requested —
    // a Digital inventory without CPM must NOT leak through in a mixed request
    assertTrue(
        queryJson.contains("cpm"), "Digital arm must keep CPM requirement in mixed requests");
    assertTrue(
        queryJson.contains("$ne"), "Non-Digital arm must be present so Classic still passes");
  }

  @Test
  void digital_only_classification_keeps_cpm_requirement() {
    String queryJson = capturePaginatedQueryJson(List.of("Digital"));

    assertTrue(queryJson.contains("cpm"), "CPM requirement must be present for Digital");
    assertTrue(queryJson.contains("Digital"), "Digital classification filter must be applied");
  }

  // -------------------------------------------------------------------------
  // findActiveInventoriesByCountryPaginated — search term ($or on name/ref/address)
  // -------------------------------------------------------------------------

  @Test
  void search_term_adds_or_over_name_referenceId_address() {
    String queryJson = capturePaginatedQueryJson(List.of("Digital"), "vast");

    assertTrue(queryJson.contains("$or"), "Search must contribute an $or clause");
    assertTrue(queryJson.contains("name"), "Search $or must cover name");
    assertTrue(queryJson.contains("referenceId"), "Search $or must cover referenceId");
    assertTrue(queryJson.contains("address"), "Search $or must cover address");
  }

  @Test
  void search_term_coexists_with_country_and_pricing_filters() {
    // Regression: search $or was previously layered onto the same Criteria that already owned the
    // pricing/archived $and, producing a malformed query. The search $or must be ANDed in as its
    // own top-level criteria so the country, $and (pricing/archived), and $or all survive.
    String queryJson = capturePaginatedQueryJson(List.of("Digital"), "vast");

    assertTrue(queryJson.contains("countryName"), "Country filter must survive alongside search");
    assertTrue(
        queryJson.contains("$and"), "Pricing/archived $and must survive alongside search $or");
    assertTrue(queryJson.contains("$or"), "Search $or must be present");
    assertTrue(
        queryJson.contains("cpm"), "Digital pricing requirement must survive alongside search");
  }

  @Test
  void no_search_term_adds_no_search_or_clause() {
    String queryJson = capturePaginatedQueryJson(List.of("Classic"));

    // Classic path has no top-level $or of its own (Digital does, via pricing) — with no search
    // term and Classic classification there should be no name/referenceId/address regex clause.
    assertFalse(
        queryJson.contains("referenceId"),
        "Without a search term no referenceId regex clause should be added");
    assertFalse(
        queryJson.contains("address"),
        "Without a search term no address regex clause should be added");
  }

  @Test
  void search_plus_programmatic_no_filter_does_not_collide_on_second_or() {
    // Regression (ERR_1001 "you can't add a second 'null' criteria"): both the search filter and a
    // programmatic-NO filter produce a top-level $or. They must each live inside their own $and
    // element rather than colliding on a single Query/Criteria $or key.
    BrowseInventoryRequestDTO filter = new BrowseInventoryRequestDTO();
    filter.setProgrammaticSupport(ProgrammaticSupport.NO);

    String queryJson =
        capturePaginatedQueryJson(List.of("Digital"), "JPN-JEK-D-00000-00048", null, filter);

    assertTrue(
        queryJson.contains("$and"), "Filters must be combined under a single top-level $and");
    assertTrue(
        queryJson.contains("programmaticDealTypes"),
        "Programmatic-NO $or must survive alongside search");
    assertTrue(
        queryJson.contains("referenceId"), "Search $or must survive alongside programmatic-NO");
    assertTrue(queryJson.contains("countryName"), "Country filter must survive");
  }

  @Test
  void search_plus_geography_filter_does_not_collide_on_second_or() {
    // Search ($or) + geography ($or) — another double-$or path that previously collided.
    RecommendationRequestDTO.GeographyTargeting geo =
        new RecommendationRequestDTO.GeographyTargeting();
    geo.setCities(List.of("Yokohama"));
    geo.setStates(List.of("Kanagawa"));

    String queryJson = capturePaginatedQueryJson(List.of("Digital"), "vast", geo, null);

    assertTrue(
        queryJson.contains("$and"), "Filters must be combined under a single top-level $and");
    assertTrue(queryJson.contains("cityName"), "Geography $or must survive alongside search");
    assertTrue(queryJson.contains("referenceId"), "Search $or must survive alongside geography");
  }

  @Test
  void blank_search_term_adds_no_search_or_clause() {
    String queryJson = capturePaginatedQueryJson(List.of("Classic"), "   ");

    assertFalse(
        queryJson.contains("referenceId"),
        "Blank search term must be ignored (no referenceId clause)");
  }

  // -------------------------------------------------------------------------
  // findActiveInventoriesByCountryWithGeographyTargeting — same pricing $or
  // -------------------------------------------------------------------------

  @Test
  void aggregation_digital_only_keeps_cpm_requirement() {
    String aggJson = captureAggregationJson(List.of("Digital"));

    assertTrue(aggJson.contains("cpm"), "CPM filter should be in aggregation pipeline for Digital");
  }

  @Test
  void aggregation_classic_not_excluded_by_cpm_requirement() {
    String aggJson = captureAggregationJson(List.of("Classic"));

    assertTrue(
        aggJson.contains("\"classification\""),
        "Classification match for Classic must still be present");
    assertTrue(aggJson.contains("Classic"), "Classic must appear in the aggregation");
    assertFalse(
        aggJson.contains("cpm"),
        "Classic-only fast path must not require CPM (the Digital pricing arm is dropped)");
  }

  @Test
  void aggregation_empty_classifications_requires_non_empty_priceTypes() {
    String aggJson = captureAggregationJson(Collections.emptyList());

    assertTrue(
        aggJson.contains("priceTypes.0"),
        "Empty classifications with no goal must require a non-empty priceTypes array");
    assertFalse(
        aggJson.contains("cpm"),
        "Digital pricing $or arm is no longer applied in the aggregation path");
    assertFalse(
        aggJson.contains("$ne"),
        "Non-Digital pricing $or arm is no longer applied in the aggregation path");
  }

  // -------------------------------------------------------------------------
  // Goal-aware priceTypes filter — paginated path
  // Note: "cpm" already appears in the Digital pricing $or, so it is NOT a
  // discriminator. "priceTypes", "monthly" and "spot" appear ONLY via the goal filter.
  // -------------------------------------------------------------------------

  @Test
  void paginated_impressions_goal_adds_priceTypes_cpm_monthly() {
    String json =
        capturePaginatedQueryJsonWithGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    assertTrue(json.contains("priceTypes"), "IMPRESSIONS must add a priceTypes filter");
    assertTrue(json.contains("monthly"), "IMPRESSIONS priceTypes filter must accept monthly");
    assertFalse(json.contains("spot"), "IMPRESSIONS must not add the spot price type");
  }

  @Test
  void paginated_reach_goal_adds_priceTypes_cpm_monthly() {
    String json = capturePaginatedQueryJsonWithGoal(RecommendationRequestDTO.CampaignGoal.REACH);

    assertTrue(json.contains("priceTypes"), "REACH must add a priceTypes filter");
    assertTrue(json.contains("monthly"), "REACH priceTypes filter must accept monthly");
    assertFalse(json.contains("spot"), "REACH must not add the spot price type");
  }

  @Test
  void paginated_sov_goal_adds_priceTypes_spot_monthly() {
    String json = capturePaginatedQueryJsonWithGoal(RecommendationRequestDTO.CampaignGoal.SOV);

    assertTrue(json.contains("priceTypes"), "SOV must add a priceTypes filter");
    assertTrue(json.contains("spot"), "SOV priceTypes filter must require spot");
    assertTrue(json.contains("monthly"), "SOV priceTypes filter must accept monthly");
  }

  @Test
  void paginated_ad_plays_goal_adds_priceTypes_spot_monthly() {
    String json = capturePaginatedQueryJsonWithGoal(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);

    assertTrue(json.contains("priceTypes"), "AD_PLAYS must add a priceTypes filter");
    assertTrue(json.contains("spot"), "AD_PLAYS priceTypes filter must require spot");
    assertTrue(json.contains("monthly"), "AD_PLAYS priceTypes filter must accept monthly");
  }

  @Test
  void paginated_carbon_goal_adds_no_priceTypes_filter() {
    String json = capturePaginatedQueryJsonWithGoal(RecommendationRequestDTO.CampaignGoal.CARBON);

    assertFalse(json.contains("priceTypes"), "CARBON must not add a priceTypes filter");
    assertFalse(json.contains("monthly"), "CARBON must not add the monthly price type");
    assertFalse(json.contains("spot"), "CARBON must not add the spot price type");
  }

  @Test
  void paginated_null_goal_adds_no_priceTypes_filter() {
    String json = capturePaginatedQueryJsonWithGoal(null);

    assertFalse(json.contains("priceTypes"), "Null goal must not add a priceTypes filter");
    assertFalse(json.contains("monthly"), "Null goal must not add the monthly price type");
    assertFalse(json.contains("spot"), "Null goal must not add the spot price type");
  }

  // -------------------------------------------------------------------------
  // Goal-aware priceTypes filter — aggregation (geography-targeting) path
  // -------------------------------------------------------------------------

  @Test
  void aggregation_impressions_goal_adds_priceTypes_cpm_monthly() {
    String json = captureAggregationJsonWithGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);

    assertTrue(json.contains("priceTypes"), "IMPRESSIONS must add a priceTypes match stage");
    assertTrue(json.contains("monthly"), "IMPRESSIONS priceTypes filter must accept monthly");
    assertFalse(json.contains("spot"), "IMPRESSIONS must not add the spot price type");
  }

  @Test
  void aggregation_reach_goal_adds_priceTypes_cpm_monthly() {
    String json = captureAggregationJsonWithGoal(RecommendationRequestDTO.CampaignGoal.REACH);

    assertTrue(json.contains("priceTypes"), "REACH must add a priceTypes match stage");
    assertTrue(json.contains("monthly"), "REACH priceTypes filter must accept monthly");
    assertFalse(json.contains("spot"), "REACH must not add the spot price type");
  }

  @Test
  void aggregation_sov_goal_adds_priceTypes_spot_monthly() {
    String json = captureAggregationJsonWithGoal(RecommendationRequestDTO.CampaignGoal.SOV);

    assertTrue(json.contains("priceTypes"), "SOV must add a priceTypes match stage");
    assertTrue(json.contains("spot"), "SOV priceTypes filter must require spot");
    assertTrue(json.contains("monthly"), "SOV priceTypes filter must accept monthly");
  }

  @Test
  void aggregation_ad_plays_goal_adds_priceTypes_spot_monthly() {
    String json = captureAggregationJsonWithGoal(RecommendationRequestDTO.CampaignGoal.AD_PLAYS);

    assertTrue(json.contains("priceTypes"), "AD_PLAYS must add a priceTypes match stage");
    assertTrue(json.contains("spot"), "AD_PLAYS priceTypes filter must require spot");
    assertTrue(json.contains("monthly"), "AD_PLAYS priceTypes filter must accept monthly");
  }

  @Test
  void aggregation_carbon_goal_adds_no_priceTypes_filter() {
    String json = captureAggregationJsonWithGoal(RecommendationRequestDTO.CampaignGoal.CARBON);

    assertFalse(json.contains("priceTypes"), "CARBON must not add a priceTypes match stage");
    assertFalse(json.contains("monthly"), "CARBON must not add the monthly price type");
    assertFalse(json.contains("spot"), "CARBON must not add the spot price type");
  }

  @Test
  void aggregation_null_goal_digital_only_requires_cpm_priceType() {
    String json = captureAggregationJsonWithGoal(null);

    assertTrue(
        json.contains("priceTypes"),
        "Digital-only with no goal must enforce a cpm priceTypes filter");
    assertTrue(json.contains("cpm"), "No-goal Digital-only priceTypes filter must require cpm");
    assertFalse(json.contains("monthly"), "No-goal priceTypes filter must not add monthly");
    assertFalse(json.contains("spot"), "No-goal priceTypes filter must not add spot");
  }
}
