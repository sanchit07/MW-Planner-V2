package com.mw.recommendation.engine.util;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestHashUtilsTest {

  @Test
  void testHashRequest_WithSameRequest_ReturnsSameHash() {
    RecommendationRequestDTO request1 = createTestRequest();
    RecommendationRequestDTO request2 = createTestRequest();

    String hash1 = RequestHashUtils.hashRequest(request1);
    String hash2 = RequestHashUtils.hashRequest(request2);

    assertEquals(hash1, hash2);
    assertNotNull(hash1);
    assertEquals(32, hash1.length()); // MD5 hash is 32 characters
  }

  @Test
  void testHashRequest_WithDifferentTopN_ReturnsSameHash() {
    RecommendationRequestDTO request1 = createTestRequest();
    request1.setTopN(10);

    RecommendationRequestDTO request2 = createTestRequest();
    request2.setTopN(20);

    String hash1 = RequestHashUtils.hashRequest(request1);
    String hash2 = RequestHashUtils.hashRequest(request2);

    // topN should be excluded from hash, so hashes should be same
    assertEquals(hash1, hash2);
  }

  @Test
  void testHashRequest_WithDifferentCountry_ReturnsDifferentHash() {
    RecommendationRequestDTO request1 = createTestRequest();
    request1.setCountry("US");

    RecommendationRequestDTO request2 = createTestRequest();
    request2.setCountry("UK");

    String hash1 = RequestHashUtils.hashRequest(request1);
    String hash2 = RequestHashUtils.hashRequest(request2);

    assertNotEquals(hash1, hash2);
  }

  @Test
  void testHashRequest_WithDifferentDates_ReturnsDifferentHash() {
    RecommendationRequestDTO request1 = createTestRequest();
    request1.setStartDate(LocalDate.of(2024, 1, 1));
    request1.setEndDate(LocalDate.of(2024, 1, 31));

    RecommendationRequestDTO request2 = createTestRequest();
    request2.setStartDate(LocalDate.of(2024, 2, 1));
    request2.setEndDate(LocalDate.of(2024, 2, 28));

    String hash1 = RequestHashUtils.hashRequest(request1);
    String hash2 = RequestHashUtils.hashRequest(request2);

    assertNotEquals(hash1, hash2);
  }

  @Test
  void testHashRequest_WithDifferentBudgetAllocation_ReturnsDifferentHash() {
    RecommendationRequestDTO request1 = createTestRequest();
    request1.setBudgetAllocation(java.util.Map.of("digital", 50.0, "classic", 50.0));

    RecommendationRequestDTO request2 = createTestRequest();
    request2.setBudgetAllocation(java.util.Map.of("digital", 40.0, "classic", 60.0));

    String hash1 = RequestHashUtils.hashRequest(request1);
    String hash2 = RequestHashUtils.hashRequest(request2);

    assertNotEquals(hash1, hash2);
  }

  @Test
  void testHashRequest_WithNullValues_HandlesGracefully() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(LocalDate.of(2024, 1, 1));
    request.setEndDate(LocalDate.of(2024, 1, 31));

    String hash = RequestHashUtils.hashRequest(request);
    assertNotNull(hash);
    assertEquals(32, hash.length());
  }

  @Test
  void differentDurationsProduceDifferentHashes() {
    RecommendationRequestDTO a = createTestRequest();
    a.setDurations(List.of(10));

    RecommendationRequestDTO b = createTestRequest();
    b.setDurations(List.of(15));

    // durations participates in the dedup hash, so different spot durations are distinct runs.
    assertNotEquals(RequestHashUtils.hashRequest(a), RequestHashUtils.hashRequest(b));
  }

  @Test
  void nullDurationsHashIdenticallyToAbsent() {
    // @JsonInclude(NON_NULL): a null durations must not appear in the serialized form, so a
    // duration-less request hashes exactly as it did before durations existed.
    RecommendationRequestDTO base = createTestRequest();
    String absentHash = RequestHashUtils.hashRequest(base);

    base.setDurations(null);
    assertEquals(absentHash, RequestHashUtils.hashRequest(base));
  }

  @Test
  void inventoryAttributeFiltersParticipateInHash() {
    // Each attribute filter scopes the run, so a change must produce a distinct hash.
    RecommendationRequestDTO base = createTestRequest();

    RecommendationRequestDTO f = createTestRequest();
    f.setFormats(List.of("ATM Screen"));
    assertNotEquals(RequestHashUtils.hashRequest(base), RequestHashUtils.hashRequest(f));

    RecommendationRequestDTO r = createTestRequest();
    r.setResolutions(List.of("1920x1080"));
    assertNotEquals(RequestHashUtils.hashRequest(base), RequestHashUtils.hashRequest(r));

    RecommendationRequestDTO c = createTestRequest();
    c.setCreativeTypes(List.of("video"));
    assertNotEquals(RequestHashUtils.hashRequest(base), RequestHashUtils.hashRequest(c));

    RecommendationRequestDTO d = createTestRequest();
    d.setDsps(List.of("LMX-ECOMMERCE"));
    assertNotEquals(RequestHashUtils.hashRequest(base), RequestHashUtils.hashRequest(d));

    RecommendationRequestDTO dt = createTestRequest();
    dt.setDealTypes(List.of("guaranteed"));
    assertNotEquals(RequestHashUtils.hashRequest(base), RequestHashUtils.hashRequest(dt));

    RecommendationRequestDTO ps = createTestRequest();
    ps.setProgrammaticSupport(com.mw.recommendation.engine.enums.ProgrammaticSupport.YES);
    assertNotEquals(RequestHashUtils.hashRequest(base), RequestHashUtils.hashRequest(ps));
  }

  @Test
  void nullAttributeFiltersHashIdenticallyToAbsent() {
    // NON_NULL: leaving the attribute filters unset must hash exactly as before they existed.
    RecommendationRequestDTO base = createTestRequest();
    String absentHash = RequestHashUtils.hashRequest(base);

    base.setFormats(null);
    base.setResolutions(null);
    base.setCreativeTypes(null);
    base.setDsps(null);
    base.setDealTypes(null);
    base.setProgrammaticSupport(null);
    assertEquals(absentHash, RequestHashUtils.hashRequest(base));
  }

  @Test
  void hashIsStableAcrossSearchKeywordsRollout() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("MY");
    request.setStartDate(LocalDate.of(2026, 7, 1));
    request.setEndDate(LocalDate.of(2026, 7, 31));
    request.setBudget(new BigDecimal("50000"));
    request.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    request.setGoalValue(1_000_000L);
    request.setExcludedInventoryIds(List.of("inv-1"));

    // Golden value captured from production code before searchKeywords existed; a change here
    // means the serialized hash form drifted and dedup against stored runs is broken.
    assertEquals("63a95e92f3f31449f54106530a5b8370", RequestHashUtils.hashRequest(request));
  }

  @Test
  void differentKeywordsProduceDifferentHashes() {
    RecommendationRequestDTO a = createKeywordTestRequest();
    RecommendationRequestDTO b = createKeywordTestRequest();

    a.setSearchKeywords(List.of("Kuala Lumpur"));
    b.setSearchKeywords(List.of("Cyberjaya"));
    assertNotEquals(RequestHashUtils.hashRequest(a), RequestHashUtils.hashRequest(b));
  }

  @Test
  void keywordOrderCaseWhitespaceAndDuplicatesDoNotChangeHash() {
    RecommendationRequestDTO a = createKeywordTestRequest();
    RecommendationRequestDTO b = createKeywordTestRequest();

    a.setSearchKeywords(List.of("Cyberjaya", "Kuala Lumpur"));
    b.setSearchKeywords(List.of("  kuala lumpur ", "CYBERJAYA", "cyberjaya"));
    assertEquals(RequestHashUtils.hashRequest(a), RequestHashUtils.hashRequest(b));
  }

  @Test
  void nullEmptyAndBlankKeywordsHashIdenticallyToAbsent() {
    RecommendationRequestDTO base = createKeywordTestRequest();
    String absentHash = RequestHashUtils.hashRequest(base);

    base.setSearchKeywords(List.of());
    assertEquals(absentHash, RequestHashUtils.hashRequest(base));

    base.setSearchKeywords(List.of("   "));
    assertEquals(absentHash, RequestHashUtils.hashRequest(base));
  }

  @Test
  void nullFalseAndEmptyProgrammaticFiltersHashIdenticallyToAbsent() {
    RecommendationRequestDTO base = createKeywordTestRequest();
    String absentHash = RequestHashUtils.hashRequest(base);

    // programmaticEnabled=false and null/empty/blank dsps must not create a distinct run —
    // preserves dedup against runs created before these fields existed.
    base.setProgrammaticEnabled(false);
    base.setDsps(List.of());
    assertEquals(absentHash, RequestHashUtils.hashRequest(base));

    base.setDsps(List.of("   "));
    assertEquals(absentHash, RequestHashUtils.hashRequest(base));
  }

  @Test
  void programmaticEnabledTrueProducesDistinctHash() {
    RecommendationRequestDTO absent = createKeywordTestRequest();
    RecommendationRequestDTO programmatic = createKeywordTestRequest();
    programmatic.setProgrammaticEnabled(true);

    assertNotEquals(
        RequestHashUtils.hashRequest(absent), RequestHashUtils.hashRequest(programmatic));
  }

  @Test
  void differentDspsProduceDifferentHashes() {
    RecommendationRequestDTO a = createKeywordTestRequest();
    RecommendationRequestDTO b = createKeywordTestRequest();

    a.setDsps(List.of("MAX"));
    b.setDsps(List.of("LMX"));
    assertNotEquals(RequestHashUtils.hashRequest(a), RequestHashUtils.hashRequest(b));
  }

  @Test
  void dspOrderAndDuplicatesDoNotChangeHash() {
    RecommendationRequestDTO a = createKeywordTestRequest();
    RecommendationRequestDTO b = createKeywordTestRequest();

    a.setDsps(List.of("MAX", "LMX"));
    b.setDsps(List.of("LMX", " MAX ", "MAX"));
    assertEquals(RequestHashUtils.hashRequest(a), RequestHashUtils.hashRequest(b));
  }

  private RecommendationRequestDTO createKeywordTestRequest() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("MY");
    request.setStartDate(LocalDate.of(2026, 7, 1));
    request.setEndDate(LocalDate.of(2026, 7, 31));
    return request;
  }

  private RecommendationRequestDTO createTestRequest() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setCountry("US");
    request.setStartDate(LocalDate.of(2024, 1, 1));
    request.setEndDate(LocalDate.of(2024, 1, 31));
    request.setProductId("product-1");
    request.setCompanyId("company-1");
    request.setTopN(10);
    return request;
  }
}
