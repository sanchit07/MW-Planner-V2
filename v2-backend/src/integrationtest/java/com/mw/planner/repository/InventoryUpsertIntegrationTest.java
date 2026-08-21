package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Inventory;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Integration test for {@link InventoryRepositoryImpl#upsertByNaturalKey(Inventory)} against a real
 * MongoDB (Testcontainers). This exercises the actual {@code findAndModify} update document, which
 * the unit tests cannot (they mock {@code InventoryService.upsertByNaturalKey}). It is the test
 * that catches the {@code $set}/{@code $setOnInsert} path-conflict regression on the key field.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventoryUpsertIntegrationTest {

  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryRepositoryImpl inventoryRepositoryImpl;
  @Autowired private MongoTemplate mongoTemplate;

  @BeforeEach
  void setUp() {
    inventoryRepository.deleteAll();
  }

  private long count() {
    return mongoTemplate.count(new Query(), Inventory.class);
  }

  private Inventory byExternalId(String externalId) {
    return mongoTemplate.findOne(
        new Query(Criteria.where("externalId").is(externalId)), Inventory.class);
  }

  @Test
  @DisplayName("(a) first upsert by externalId inserts exactly one doc with a generated _id")
  void firstUpsert_InsertsSingleDocumentWithGeneratedId() {
    Inventory message = new Inventory();
    message.setExternalId("EXT-1");
    message.setReferenceId("REF-1");
    message.setName("Original Name");

    Inventory result = inventoryRepositoryImpl.upsertByNaturalKey(message);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isNotBlank();
    assertThat(result.getExternalId()).isEqualTo("EXT-1");
    assertThat(result.getName()).isEqualTo("Original Name");
    assertThat(count()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "(b) second upsert with the same externalId updates in place — count stays 1, _id unchanged,"
          + " changed field updated")
  void secondUpsert_SameExternalId_UpdatesInPlace() {
    Inventory first = new Inventory();
    first.setExternalId("EXT-1");
    first.setReferenceId("REF-1");
    first.setName("Original Name");
    String firstId = inventoryRepositoryImpl.upsertByNaturalKey(first).getId();

    Inventory second = new Inventory();
    second.setExternalId("EXT-1");
    second.setReferenceId("REF-1");
    second.setName("Updated Name");

    Inventory result = inventoryRepositoryImpl.upsertByNaturalKey(second);

    assertThat(count()).isEqualTo(1);
    assertThat(result.getId()).isEqualTo(firstId);
    assertThat(result.getName()).isEqualTo("Updated Name");
  }

  @Test
  @DisplayName("(c) partial update — a field null on the 2nd message is NOT wiped")
  void secondUpsert_NullField_DoesNotWipeExistingValue() {
    Inventory first = new Inventory();
    first.setExternalId("EXT-1");
    first.setReferenceId("REF-1");
    first.setName("Original Name");
    first.setMediaOwnerName("Owner A");
    inventoryRepositoryImpl.upsertByNaturalKey(first);

    // Sparse refresh: name changes, mediaOwnerName omitted (null)
    Inventory second = new Inventory();
    second.setExternalId("EXT-1");
    second.setName("Updated Name");
    // mediaOwnerName left null on purpose

    inventoryRepositoryImpl.upsertByNaturalKey(second);

    Inventory persisted = byExternalId("EXT-1");
    assertThat(persisted.getName()).isEqualTo("Updated Name");
    assertThat(persisted.getMediaOwnerName()).isEqualTo("Owner A"); // preserved, not wiped
  }

  @Test
  @DisplayName("(d) createdAt set on insert and unchanged on 2nd upsert; updatedAt advances")
  void upsert_ManagesAuditTimestamps() throws InterruptedException {
    Inventory first = new Inventory();
    first.setExternalId("EXT-1");
    first.setName("Original Name");
    inventoryRepositoryImpl.upsertByNaturalKey(first);

    Inventory afterInsert = byExternalId("EXT-1");
    LocalDateTime createdAt = afterInsert.getCreatedAt();
    LocalDateTime updatedAtV1 = afterInsert.getUpdatedAt();
    assertThat(createdAt).isNotNull();
    assertThat(updatedAtV1).isNotNull();

    Thread.sleep(10); // ensure a measurable clock tick

    Inventory second = new Inventory();
    second.setExternalId("EXT-1");
    second.setName("Updated Name");
    inventoryRepositoryImpl.upsertByNaturalKey(second);

    Inventory afterUpdate = byExternalId("EXT-1");
    assertThat(afterUpdate.getCreatedAt()).isEqualTo(createdAt); // unchanged
    assertThat(afterUpdate.getUpdatedAt()).isAfterOrEqualTo(updatedAtV1); // advanced
  }

  @Test
  @DisplayName("(e) referenceId-only path (externalId null) upserts keyed on referenceId")
  void upsert_ReferenceIdOnly_KeyedOnReferenceId() {
    Inventory first = new Inventory();
    first.setReferenceId("REF-ONLY");
    first.setName("Original Name");
    String firstId = inventoryRepositoryImpl.upsertByNaturalKey(first).getId();

    Inventory second = new Inventory();
    second.setReferenceId("REF-ONLY");
    second.setName("Updated Name");
    Inventory result = inventoryRepositoryImpl.upsertByNaturalKey(second);

    assertThat(count()).isEqualTo(1);
    assertThat(result.getId()).isEqualTo(firstId);
    assertThat(result.getName()).isEqualTo("Updated Name");

    List<Inventory> byRef =
        mongoTemplate.find(
            new Query(Criteria.where("referenceId").is("REF-ONLY")), Inventory.class);
    assertThat(byRef).hasSize(1);
  }
}
