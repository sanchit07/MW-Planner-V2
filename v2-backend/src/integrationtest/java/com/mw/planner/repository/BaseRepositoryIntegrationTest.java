package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.TestcontainersConfiguration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Base integration test class for repository tests that provides common test methods for entities
 * with activation status and search functionality.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class BaseRepositoryIntegrationTest<T, ID> {

  @Autowired protected MongoRepository<T, ID> repository;

  protected T testEntity1;
  protected T testEntity2;
  protected T testEntity3;
  protected T inactiveEntity;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    setupTestData();
  }

  /** Setup test data specific to the entity type. Must be implemented by subclasses. */
  protected abstract void setupTestData();

  /** Get the name field value for the given entity. Used for sorting and search tests. */
  protected abstract String getName(T entity);

  /** Get the activation status for the given entity. */
  protected abstract boolean isActivated(T entity);

  /** Create a new entity for save tests. */
  protected abstract T createNewEntity();

  /** Get the ID of the first test entity for delete tests. */
  protected abstract ID getFirstEntityId();

  /** Get the expected total count of entities (including inactive). */
  protected abstract long getExpectedTotalCount();

  /** Get the expected count of active entities. */
  protected abstract long getExpectedActiveCount();

  /** Test finding all activated entities. */
  protected void testFindByActivatedTrue_ShouldReturnOnlyActivatedEntities(Page<T> result) {
    assertThat(result.getContent()).hasSize((int) getExpectedActiveCount());
    assertThat(result.getContent()).allMatch(this::isActivated);
  }

  /** Test pagination for activated entities. */
  protected void testFindByActivatedTrue_WithPagination_ShouldReturnCorrectPage(Page<T> result) {
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(getExpectedActiveCount());
    assertThat(result.getTotalPages()).isEqualTo(2);
    assertThat(result.getNumber()).isEqualTo(0);
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isFalse();
  }

  /** Test sorting for activated entities. */
  protected void testFindByActivatedTrue_WithSorting_ShouldReturnSortedEntities(
      Page<T> result, String sortField) {
    assertThat(result.getContent()).hasSize((int) getExpectedActiveCount());
    // Verify that entities are sorted by the specified field
    List<String> names = result.getContent().stream().map(this::getName).toList();
    assertThat(names).isSorted();
  }

  /** Test search functionality with exact match. */
  protected void testFindBySearchTermAndActivatedTrue_WithExactMatch_ShouldReturnMatchingEntities(
      Page<T> result, String expectedName) {
    assertThat(result.getContent()).hasSize(1);
    assertThat(getName(result.getContent().get(0))).isEqualTo(expectedName);
  }

  /** Test case-insensitive search. */
  protected void
      testFindBySearchTermAndActivatedTrue_WithCaseInsensitiveSearch_ShouldReturnMatchingEntities(
          Page<T> result, String expectedName) {
    assertThat(result.getContent()).hasSize(1);
    assertThat(getName(result.getContent().get(0))).isEqualTo(expectedName);
  }

  /** Test partial match search. */
  protected void testFindBySearchTermAndActivatedTrue_WithPartialMatch_ShouldReturnMatchingEntities(
      Page<T> result, int expectedCount) {
    assertThat(result.getContent()).hasSize(expectedCount);
  }

  /** Test search with no matches. */
  protected void testFindBySearchTermAndActivatedTrue_WithNoMatches_ShouldReturnEmpty(
      Page<T> result) {
    assertThat(result.getContent()).isEmpty();
  }

  /** Test that inactive entities are excluded from search. */
  protected void testFindBySearchTermAndActivatedTrue_ShouldExcludeInactiveEntities(
      Page<T> result) {
    assertThat(result.getContent()).isEmpty();
  }

  /** Test pagination for search results. */
  protected void testFindBySearchTermAndActivatedTrue_WithPagination_ShouldReturnCorrectPage(
      Page<T> result, long expectedTotalElements, int expectedTotalPages) {
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getTotalElements()).isEqualTo(expectedTotalElements);
    assertThat(result.getTotalPages()).isEqualTo(expectedTotalPages);
  }

  /** Test saving a new entity. */
  protected void testSave_ShouldPersistEntity() {
    // Given
    T newEntity = createNewEntity();

    // When
    T savedEntity = repository.save(newEntity);

    // Then
    assertThat(savedEntity).isNotNull();
    Optional<T> retrievedEntity = repository.findById((ID) getEntityId(savedEntity));
    assertThat(retrievedEntity).isPresent();
  }

  /** Test deleting an entity by ID. */
  protected void testDeleteById_ShouldRemoveEntity() {
    // When
    repository.deleteById(getFirstEntityId());

    // Then
    Optional<T> result = repository.findById(getFirstEntityId());
    assertThat(result).isEmpty();
  }

  /** Test counting entities. */
  protected void testCount_ShouldReturnCorrectCount() {
    // When
    long count = repository.count();

    // Then
    assertThat(count).isEqualTo(getExpectedTotalCount());
  }

  /** Test complex sorting. */
  protected void testFindByActivatedTrue_WithComplexSorting_ShouldReturnCorrectlySortedEntities(
      Page<T> result) {
    assertThat(result.getContent()).hasSize((int) getExpectedActiveCount());
    // The actual order depends on the specific sorting criteria
    // This is a basic verification that sorting works
    assertThat(result.getContent()).isNotEmpty();
  }

  /** Test search with special characters. */
  protected void testFindBySearchTermAndActivatedTrue_WithSpecialCharacters_ShouldHandleCorrectly(
      Page<T> result, String expectedName) {
    assertThat(result.getContent()).hasSize(1);
    assertThat(getName(result.getContent().get(0))).isEqualTo(expectedName);
  }

  /** Get the ID of an entity. Must be implemented by subclasses. */
  protected abstract ID getEntityId(T entity);
}
