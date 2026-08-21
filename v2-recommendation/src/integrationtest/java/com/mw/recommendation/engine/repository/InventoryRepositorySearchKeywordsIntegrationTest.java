package com.mw.recommendation.engine.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import com.mw.recommendation.engine.domain.Inventory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class InventoryRepositorySearchKeywordsIntegrationTest {

  private static final String COUNTRY = "SearchKeywordsTestland";

  @Autowired private InventoryRepositoryImpl repository;
  @Autowired private MongoTemplate mongoTemplate;

  @BeforeEach
  void seed() {
    mongoTemplate.save(
        inventory("KLCC Digital Tower", "REF-KL-001", "Jalan Ampang", "Kuala Lumpur", "WP KL"));
    mongoTemplate.save(
        inventory("Cyber Heights Board", "REF-CJ-002", "Persiaran APEC", "Cyberjaya", "Selangor"));
    mongoTemplate.save(
        inventory("Penang Bridge Banner", "REF-PG-003", "Gelugor", "George Town", "Penang"));
  }

  @AfterEach
  void cleanUp() {
    mongoTemplate.remove(
        new Query(Criteria.where("locationHierarchy.countryName").is(COUNTRY)), "inventories");
  }

  private Inventory inventory(
      String name, String referenceId, String address, String city, String state) {
    Inventory inv = new Inventory();
    inv.setInventoryId("kw-test-" + UUID.randomUUID());
    inv.setName(name);
    inv.setReferenceId(referenceId);
    inv.setAddress(address);
    inv.setClassification("Classic");
    inv.setArchived(false);
    inv.setPrices(List.of(Inventory.PriceModel.builder().cpm(5.0).currency("USD").build()));
    Inventory.LocationHierarchy lh = new Inventory.LocationHierarchy();
    lh.setCountryName(COUNTRY);
    lh.setCityName(city);
    lh.setStateName(state);
    inv.setLocationHierarchy(lh);
    return inv;
  }

  private List<Inventory> fetch(List<String> keywords) {
    return repository.findActiveInventoriesByCountryWithGeographyTargeting(
        COUNTRY,
        null,
        null,
        null,
        null,
        List.of("Classic"),
        keywords,
        null,
        null,
        null,
        null,
        null,
        null,
        false);
  }

  @Test
  void nullAndEmptyKeywordsReturnAllInventories() {
    assertEquals(3, fetch(null).size());
    assertEquals(3, fetch(List.of()).size());
  }

  @Test
  void cityKeywordMatchesCaseInsensitively() {
    List<Inventory> result = fetch(List.of("kuala lumpur"));
    assertEquals(1, result.size());
    assertEquals("KLCC Digital Tower", result.get(0).getName());
  }

  @Test
  void partialSubstringMatchesAcrossFields() {
    // "cyber" hits cityName "Cyberjaya" AND name "Cyber Heights Board" (same doc)
    assertEquals(1, fetch(List.of("cyber")).size());
    // "REF-PG" hits referenceId only
    assertEquals(1, fetch(List.of("REF-PG")).size());
    // "Jalan" hits address only
    assertEquals(1, fetch(List.of("Jalan")).size());
  }

  @Test
  void multipleKeywordsAreOrSemantics() {
    assertEquals(2, fetch(List.of("Kuala Lumpur", "Cyberjaya")).size());
  }

  @Test
  void noMatchReturnsEmpty() {
    assertTrue(fetch(List.of("Atlantis")).isEmpty());
  }

  @Test
  void regexMetacharactersAreLiteral() {
    assertTrue(fetch(List.of("K.*")).isEmpty()); // would match everything if unescaped
  }
}
